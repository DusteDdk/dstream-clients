# -*- coding: utf-8 -*-

import sys
import json
import urllib.parse
import urllib.request

import os
import xbmc
import xbmcgui
import xbmcplugin
import xbmcaddon
import xbmcvfs
import base64
import lzma
import sqlite3
import time
from typing import Dict, Optional

ADDON = xbmcaddon.Addon()
ADDON_ID = ADDON.getAddonInfo("id")
HANDLE = int(sys.argv[1])
BASE_URL = sys.argv[0]


def get_api_credentials():
    username = ADDON.getSetting("api_username").strip()
    password = ADDON.getSetting("api_password").strip()
    return username, password


USERNAME, PASSWORD = get_api_credentials()
token = f"{USERNAME}:{PASSWORD}".encode("ascii")
encoded_auth = "Basic " + base64.b64encode(token).decode("ascii")
streamAuth = f"|Authorization= {encoded_auth}"

def get_api_base_url() -> str:
    value = ADDON.getSetting("api_base_url").strip()
    if not value:
        value = "https://domain.with.dstream.tld"
    return value.rstrip("/")

def get_smb_base_path() -> str:
    value = ADDON.getSetting("smb_base_path").strip()
    if not value:
        return "smb://1.2.3.4/share/with/music"
    return value.rstrip("/")

def get_playback_mode() -> str:
    value = ADDON.getSetting("playback_mode").strip()
    return "http" if value == "1" else "smb"

def get_menu_order() -> str:
    value = ADDON.getSetting("menu_order").strip()
    return "random_first" if value == "1" else "search_first"


addon_path = os.path.dirname(__file__)
media_path = os.path.join(addon_path, "resources", "icons")

icons: Dict[str, str] = {}
icons["flac"] = os.path.join(media_path, "flac.png")
icons["mpeg 1 layer 3"] = os.path.join(media_path, "mp3.png")
icons["vorbis"] = os.path.join(media_path, "vorbis.png")
icons["wav"] = os.path.join(media_path, "wav.png")
icons["wma"] = os.path.join(media_path, "wma.png")
icons["audio"] = os.path.join(media_path, "audio.png")
icons["random"] = os.path.join(media_path, "random.png")

def getIcon(codec: str):
    global icons
    if icons.get(codec):
        return icons[codec]
    return "audio.png"

def log(msg):
    xbmc.log(f"[{ADDON_ID}] {msg}", level=xbmc.LOGINFO)


def get_params():
    # sys.argv[2] is like "?action=search&..."
    if len(sys.argv) < 3 or not sys.argv[2]:
        return {}
    qs = sys.argv[2][1:] if sys.argv[2].startswith("?") else sys.argv[2]
    return dict(urllib.parse.parse_qsl(qs))


def build_url(params: dict) -> str:
    return BASE_URL + "?" + urllib.parse.urlencode(params)

def get_db_path() -> str:
    profile_dir = xbmcvfs.translatePath(ADDON.getAddonInfo("profile"))
    if not xbmcvfs.exists(profile_dir):
        xbmcvfs.mkdirs(profile_dir)
    return os.path.join(profile_dir, "plays.sqlite3")

def ensure_metadata_columns(conn: sqlite3.Connection) -> None:
    expected = {
        "file_path": "TEXT",
        "title": "TEXT",
        "artist_name": "TEXT",
        "album_name": "TEXT",
        "duration": "INTEGER",
        "year": "INTEGER",
        "codec": "TEXT",
    }
    cursor = conn.execute("PRAGMA table_info(plays)")
    existing = {row[1] for row in cursor.fetchall()}
    for column, col_type in expected.items():
        if column not in existing:
            conn.execute(f"ALTER TABLE plays ADD COLUMN {column} {col_type}")

def init_db(conn: sqlite3.Connection) -> None:
    conn.execute(
        "CREATE TABLE IF NOT EXISTS plays ("
        "track_id TEXT PRIMARY KEY, "
        "play_count INTEGER NOT NULL DEFAULT 0, "
        "last_played INTEGER, "
        "file_path TEXT, "
        "title TEXT, "
        "artist_name TEXT, "
        "album_name TEXT, "
        "duration INTEGER, "
        "year INTEGER, "
        "codec TEXT"
        ")"
    )
    ensure_metadata_columns(conn)
    conn.commit()

def to_int(value) -> Optional[int]:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None

def record_metadata(result: dict) -> None:
    track_id = result.get("id")
    if not track_id:
        return
    db_path = get_db_path()
    with sqlite3.connect(db_path) as conn:
        init_db(conn)
        conn.execute(
            "INSERT OR IGNORE INTO plays (track_id, play_count, last_played) "
            "VALUES (?, 0, NULL)",
            (track_id,),
        )
        conn.execute(
            "UPDATE plays SET file_path = ?, title = ?, artist_name = ?, "
            "album_name = ?, duration = ?, year = ?, codec = ? "
            "WHERE track_id = ?",
            (
                result.get("file"),
                result.get("title"),
                result.get("artistName"),
                result.get("albumName"),
                to_int(result.get("duration")),
                to_int(result.get("year")),
                result.get("codec"),
                track_id,
            ),
        )
        conn.commit()

def record_play(track_id: str) -> None:
    if not track_id:
        return
    db_path = get_db_path()
    with sqlite3.connect(db_path) as conn:
        init_db(conn)
        now = int(time.time())
        conn.execute(
            "INSERT OR IGNORE INTO plays (track_id, play_count, last_played) "
            "VALUES (?, 0, NULL)",
            (track_id,),
        )
        conn.execute(
            "UPDATE plays SET play_count = play_count + 1, last_played = ? "
            "WHERE track_id = ?",
            (now, track_id),
        )
        conn.commit()

def get_metadata(track_id: str) -> Optional[dict]:
    if not track_id:
        return None
    db_path = get_db_path()
    with sqlite3.connect(db_path) as conn:
        init_db(conn)
        cursor = conn.execute(
            "SELECT file_path, title, artist_name, album_name, duration, year, codec "
            "FROM plays WHERE track_id = ?",
            (track_id,),
        )
        row = cursor.fetchone()
        if not row:
            return None
        return {
            "id": track_id,
            "file": row[0],
            "title": row[1] or "",
            "artistName": row[2] or "",
            "albumName": row[3] or "",
            "duration": row[4],
            "year": row[5],
            "codec": row[6] or "",
        }


def http_get_json(search_text: str):
    global encoded_auth
    timeout_s = 10

    if len(search_text) > 0:
        url = get_api_base_url() + "/tracks.json?" + urllib.parse.urlencode({"q": search_text})
    else:
        url = get_api_base_url() + "/random.json"

    req = urllib.request.Request(
        url,
        headers={
            "Accept": "application/json",
            "Authorization": encoded_auth,
            "User-Agent": f"Kodi/{xbmc.getInfoLabel('System.BuildVersion')} ({ADDON_ID})",
        },
        method="GET",
    )

    with urllib.request.urlopen(req, timeout=timeout_s) as resp:
        raw = resp.read().decode("utf-8", errors="replace")
        return json.loads(raw)


def resultToListItem(result: dict):
        label = build_label(result)

        codec_label = result.get("codec", "")
        codec = codec_label.lower() if codec_label else ""
        file_path = result["file"]
        file_lower = file_path.lower()

        if codec == "_" and file_lower.endswith(".mp3"):
            codec = "mp3"
        if codec == "_" and file_lower.endswith(".flac"):
            codec = "flac"
        if codec == "_" and file_lower.endswith(".wav"):
            codec = "wav"
        if codec == "_" and file_lower.endswith(".ogg"):
            codec = "ogg"
        if codec_label.startswith("Windows Media"):
            codec = "wma"

        if icons.get(codec) is None and codec_label != "_":
            label = label + " " + codec_label

        li = xbmcgui.ListItem(label=label)
        li.setProperty("IsPlayable", "true")
        art = { "icon": getIcon(codec) }
        li.setArt(art)
        tag = li.getMusicInfoTag()
        tag.setTitle(result.get("title", ""))

        artist = result.get("artistName", "-")
        if artist != "-":
            tag.setArtist(artist)

        album = result.get("albumName", "-")
        if album != "-":
            tag.setAlbum(album)

        duration = result.get("duration")
        if duration:
            tag.setDuration(int(duration))
            li.setProperty("Duration", str(int(duration)))

        year = result.get("year")
        if year:
            tag.setYear(int(year))

        return li


def build_label(result: dict) -> str:
    label = result.get("title") or ""
    if label == "Untitled" or not label or result.get("artistName") == "-":
        label = result["file"].rsplit("/", 1)[-1]
    else:
        label = f'{result["artistName"]} – {label}'
    return label

def build_stream_url(result: dict) -> str:
    if get_playback_mode() == "http":
        return get_api_base_url() + result["file"] + streamAuth
    return get_smb_base_path() + result["file"][len("/music"):]


def getList(results: list):
    items = []
    global icons
    for r in results:
        record_metadata(r)
        li = resultToListItem(r)
        play_url = build_url({"action": "play", "id": r.get("id", "")})

        items.append((play_url, li, False))
    return items


def add_root_menu():
    xbmcplugin.setPluginCategory(HANDLE, "Search")
    xbmcplugin.setContent(HANDLE, "songs")

    rli = xbmcgui.ListItem(label="? Random")
    rli.setArt({"icon": getIcon("random")})

    sli = xbmcgui.ListItem(label="= Search…")
    sli.setArt({"icon": "DefaultAddonsSearch.png"})

    if get_menu_order() == "random_first":
        xbmcplugin.addDirectoryItem(HANDLE, build_url({"action": "root"}), rli, isFolder=True)
        xbmcplugin.addDirectoryItem(HANDLE, build_url({"action": "search"}), sli, isFolder=True)
    else:
        xbmcplugin.addDirectoryItem(HANDLE, build_url({"action": "search"}), sli, isFolder=True)
        xbmcplugin.addDirectoryItem(HANDLE, build_url({"action": "root"}), rli, isFolder=True)

    mpli = xbmcgui.ListItem(label="* Most played")
    mpli.setArt({"icon": "DefaultMusicRecentlyPlayed.png"})
    xbmcplugin.addDirectoryItem(HANDLE, build_url({"action": "most_played"}), mpli, isFolder=True)

    rpli = xbmcgui.ListItem(label="+ Recently played")
    rpli.setArt({"icon": "DefaultMusicRecentlyAdded.png"})
    xbmcplugin.addDirectoryItem(HANDLE, build_url({"action": "recently_played"}), rpli, isFolder=True)

    randomResults = http_get_json("")
    items = getList(randomResults)

    xbmcplugin.addDirectoryItems(HANDLE, items, len(items))
    xbmcplugin.endOfDirectory(HANDLE)


def do_search():
    # Prompt
    q = xbmcgui.Dialog().input("Search", type=xbmcgui.INPUT_ALPHANUM)
    if not q:
        xbmcplugin.endOfDirectory(HANDLE, succeeded=True, cacheToDisc=False)
        return

    try:
        results = http_get_json(q)
    except Exception as e:
        xbmcgui.Dialog().notification("Search failed", str(e), xbmcgui.NOTIFICATION_ERROR, 5000)
        xbmcplugin.endOfDirectory(HANDLE, succeeded=False, cacheToDisc=False)
        return

    xbmcplugin.setPluginCategory(HANDLE, f"Results for: {q}")
    xbmcplugin.setContent(HANDLE, "songs")

    items = getList(results)
    xbmcplugin.addDirectoryItems(HANDLE, items, len(items))
    xbmcplugin.endOfDirectory(HANDLE)

def query_stats(order_by: str, where_clause: str) -> list:
    db_path = get_db_path()
    with sqlite3.connect(db_path) as conn:
        init_db(conn)
        cursor = conn.execute(
            "SELECT track_id, play_count, last_played, file_path, title, "
            "artist_name, album_name, duration, year, codec "
            "FROM plays "
            f"{where_clause} "
            f"ORDER BY {order_by} "
            "LIMIT 100"
        )
        rows = cursor.fetchall()
    results = []
    for row in rows:
        if not row[3]:
            continue
        results.append(
            {
                "id": row[0],
                "play_count": row[1],
                "last_played": row[2],
                "file": row[3],
                "title": row[4] or "",
                "artistName": row[5] or "",
                "albumName": row[6] or "",
                "duration": row[7],
                "year": row[8],
                "codec": row[9] or "",
            }
        )
    return results

def do_most_played():
    xbmcplugin.setPluginCategory(HANDLE, "Most played")
    xbmcplugin.setContent(HANDLE, "songs")
    results = query_stats("play_count DESC, last_played DESC", "WHERE play_count > 0")
    items = []
    for r in results:
        li = resultToListItem(r)
        label = f"{build_label(r)} ({r['play_count']})"
        li.setLabel(label)
        play_url = build_url({"action": "play", "id": r.get("id", "")})
        items.append((play_url, li, False))
    xbmcplugin.addDirectoryItems(HANDLE, items, len(items))
    xbmcplugin.endOfDirectory(HANDLE)

def do_recently_played():
    xbmcplugin.setPluginCategory(HANDLE, "Recently played")
    xbmcplugin.setContent(HANDLE, "songs")
    results = query_stats("last_played DESC", "WHERE last_played IS NOT NULL")
    items = []
    for r in results:
        li = resultToListItem(r)
        played_date = time.strftime("%Y-%m-%d", time.localtime(r["last_played"]))
        label = f"{build_label(r)} ({played_date})"
        li.setLabel(label)
        play_url = build_url({"action": "play", "id": r.get("id", "")})
        items.append((play_url, li, False))
    xbmcplugin.addDirectoryItems(HANDLE, items, len(items))
    xbmcplugin.endOfDirectory(HANDLE)

def do_play(params):
    track_id = params.get("id", "")
    if not track_id:
        xbmcgui.Dialog().notification("Playback failed", "Missing track id", xbmcgui.NOTIFICATION_ERROR, 5000)
        return

    result = get_metadata(track_id)
    if not result or not result.get("file"):
        xbmcgui.Dialog().notification("Playback failed", "Missing metadata", xbmcgui.NOTIFICATION_ERROR, 5000)
        return

    li = resultToListItem(result)
    stream = build_stream_url(result)
    li.setPath(path=stream)
    record_play(track_id)
    xbmcplugin.setResolvedUrl(HANDLE, True, li)


def router():
    params = get_params()
    action = params.get("action")

    if action == "search":
        do_search()
    elif action == "most_played":
        do_most_played()
    elif action == "recently_played":
        do_recently_played()
    elif action == "play":
        do_play(params)
    else:
        add_root_menu()


if __name__ == "__main__":
    router()
