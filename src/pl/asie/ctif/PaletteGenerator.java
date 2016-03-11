package pl.asie.ctif;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public class PaletteGenerator {
    public PaletteGenerator() {

    }

    public Color[] generate(BufferedImage image, Color[] base) {
        int[] pixels = Utils.getRGB(Utils.colorRemap(image, 16));
        int[] table = new int[16];
        int i = 0;
        int j = 0;

        while (i < table.length && j < pixels.length) {
            int col = pixels[j++];
            boolean found = false;
            for (int k = 0; k < i; k++) {
                if (table[k] == col) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                table[i++] = col;
            }
        }

        Color[] out = Arrays.copyOf(base, base.length);
        for (int k = 0; k < i; k++) {
            out[k] = new Color(table[k] | 0xFF000000);
        }
        return out;

        /*SortedMap<Double, Integer> weightedMap =new TreeMap<Double, Integer>();

        for (i = 0; i < table.length; i++) {
            if (table[i] == 0) {
                continue;
            }
            double d = 0;
            for (j = 16; j < base.length; j++) {
                d += Utils.getColorDistance(table[i], base[j].getRGB());
            }

            for (j = 0; j < table.length; j++) {
                if (table[i] == 0) {
                    continue;
                }
                //d += getDistance(table[i], table[j]);
            }
            System.out.println(d + " - " + new Color(table[i]).toString());
            weightedMap.put(d, i);
        }

        Color[] out = Arrays.copyOf(base, 256);

        for (Map.Entry<Double, Integer> entry : weightedMap.entrySet()) {
            System.out.println(new Color(table[entry.getValue()]).toString());
            out[i] = new Color(table[entry.getValue()]);
            i++;
            if (i >= 16) {
                break;
            }
        }*/
    }
}
