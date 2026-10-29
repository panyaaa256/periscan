PeriScan can now be installed on the server too (Minecraft 1.21.9 and newer): the server then scans the whole area, wherever the players are. YACL is no longer required on any Minecraft version.

### Added
- Add scanning on the server (Minecraft 1.21.9 and newer). When the server also has PeriScan, it scans the whole area for the players, including chunks no player has loaded, and highlights follow block changes anywhere in the area. The server only needs PeriScan and Fabric API. Singleplayer works this way as is
- Add a chat message with the number of chunks in the area that are not generated yet, as the server does not scan them

### Changed
- Make YACL optional. Without it, PeriScan runs with the default settings, and the settings screens say that YACL is needed instead of opening
