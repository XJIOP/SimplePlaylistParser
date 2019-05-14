# SimplePlaylistParser
Simple m3u playlist parser for IPTV, etc.

**System requirements:**
- Windows / Linux
- Java (JRE) 8+

**How to use:**
- Download latest release SimplePlaylistParser.zip
- Edit settings.cfg
- Edit filter.list if you want specific channels

**Available Settings:**
- URL to parse playlist
- Playlist charset
- Parse interval
- Filter specific channels name with ability to split into groups
- Local server to access playlist (http://192.168.1.*:8765/playlist.m3u)

**Run as service:**
- launch.jar

**Run as single parsing:**
- launch.jar --input (path to playlist file or url) --output (playlist output filename)

------------

### Example filter.list

**Specific channels**
Channell name 1
Channell name 2
Channell name 3
Channell name 4
Channell name 5

**Specific channels with ability to split into groups**
[Group name 1]
Channell name 1
Channell name 2
Channell name 3
[Group name 2]
Channell name 4
Channell name 5