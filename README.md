# SimplePlaylistParser
Simple m3u playlist parser for IPTV, etc.

**Available Settings:**
- URL to parse playlist
- Playlist charset
- Parse interval
- Filter specific channels name with ability to split into groups
- Local server to access playlist (http://192.168.1.*:8765/playlist.m3u)

**How to use:**
- Download latest release SimplePlaylistParser.zip
- Edit settings.cfg
- Edit filter.list if you want specific channels

**Run as service:**
- launch.jar

**Run as single parsing:**
- launch.jar --input (path to playlist file or url) --output (playlist output filename)

**System requirements:**
- Windows / Linux
- Java (JRE) 8+

------------

### Example filter.list

**Specific channels**  
Channel name 1  
Channel name 2  
Channel name 3  
Channel name 4  
Channel name 5  

**Specific channels with ability to split into groups**  
[Group name 1]  
Channel name 1  
Channel name 2  
Channel name 3  
[Group name 2]  
Channel name 4  
Channel name 5