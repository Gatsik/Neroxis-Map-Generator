package exporter;

import map.SCMap;

import java.io.*;
import java.nio.file.Path;

public strictfp class ScenarioExporter {

    public static void exportScenario(Path folderPath, String mapName, SCMap map) throws IOException {
        File file = folderPath.resolve(mapName + "_scenario.lua").toFile();
        String mapFolder = folderPath.getFileName().toString();
        boolean status = file.createNewFile();
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        out.writeBytes("version = 3\n");
        out.writeBytes("ScenarioInfo = {\n");
        out.writeBytes("  name = \"" + map.getName() + "\",\n");
        out.writeBytes("  description = \"" + map.getDescription() + "\",\n");
        out.writeBytes("  type = 'skirmish',\n");
        out.writeBytes("  starts = true,\n");
        out.writeBytes("  preview = '',\n");
        out.writeBytes("  size = {" + map.getSize() + ", " + map.getSize() + "},\n");
        out.writeBytes("  map = '/maps/" + mapFolder + "/" + mapName + ".scmap',\n");
        out.writeBytes("  map_version = 1,\n");
        out.writeBytes("  save = '/maps/" + mapFolder + "/" + mapName + "_save.lua',\n");
        out.writeBytes("  script = '/maps/" + mapFolder + "/" + mapName + "_script.lua',\n");
        out.writeBytes("  hidePreviewMarkers = " + !map.isGeneratePreview() + ",\n");
        out.writeBytes("  norushradius = " + map.getNoRushRadius() + ",\n");
        for (int i = 0; i < map.getSpawnCount(); i++) {
            out.writeBytes("  norushoffsetX_ARMY_" + map.getSpawn(i).getId() + " = " + map.getSpawn(i).getNoRushOffset().x + ",\n");
            out.writeBytes("  norushoffsetY_ARMY_" + map.getSpawn(i).getId() + " = " + map.getSpawn(i).getNoRushOffset().y + ",\n");
        }
        out.writeBytes("  Configurations = {\n");
        out.writeBytes("    ['standard'] = {\n");
        out.writeBytes("      teams = {\n");
        out.writeBytes("        {\n");
        out.writeBytes("          name = 'FFA',\n");
        out.writeBytes("          armies = {");
        for (int i = 0; i < map.getSpawnCount(); i++) {
            out.writeBytes("'ARMY_" + (i + 1) + "'");
            if (i < map.getSpawnCount() - 1)
                out.writeBytes(",");
        }
        out.writeBytes("},\n");
        out.writeBytes("        },\n");
        out.writeBytes("      },\n");
        out.writeBytes("      customprops = {\n");
        out.writeBytes("        ['ExtraArmies'] = STRING( 'ARMY_17 NEUTRAL_CIVILIAN' ),\n");
        out.writeBytes("      },\n");
        out.writeBytes("    },\n");
        out.writeBytes("  },\n");
        out.writeBytes("}\n");

        out.flush();
        out.close();
    }
}
