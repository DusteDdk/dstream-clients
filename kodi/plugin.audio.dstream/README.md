plugin.audio.dstream
====================

Kodi audio plugin that searches and plays music from the dstream service.
It exposes Search, Random, Most played, and Recently played entries, and
resolves playback via SMB or HTTP.

Features
--------
- Search the dstream API for tracks.
- Random track list from the API.
- Local play history for Most played and Recently played (stored in the addon profile).
- Playback over SMB or HTTP.

Settings
--------
- API username/password: used for Basic Auth to the dstream API.
- API base URL: base address for API and HTTP playback, no trailing slash.
- SMB base path: base path for SMB playback, no trailing slash.
- Playback mode: choose SMB or HTTP.
- Menu order: choose whether Search or Random is listed first.

Notes
-----
- HTTP playback uses the API base URL plus the file path returned by the API.
- SMB playback strips the /music prefix from the API file path and appends the file path the SMB base path.
