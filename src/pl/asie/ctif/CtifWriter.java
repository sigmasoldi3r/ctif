package pl.asie.ctif;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class CtifWriter {
    public CtifWriter() {

    }

    public BufferedImage write(OutputStream stream, BufferedImage image, Color[] palette) throws IOException {
		BufferedImage output = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);

		int pw = Main.MODE.pw;
		int ph = Main.MODE.ph;
        int cw = image.getWidth() / pw;
        int ch = image.getHeight() / ph;

		stream.write('C');
		stream.write('T');
		stream.write('I');
		stream.write('F');

		stream.write(1); // Header version
		stream.write(0); // Platform variant (0 - default)
		stream.write(Main.MODE.platformId);
		stream.write(Main.MODE.platformId >> 8); // Platform ID
        stream.write(cw & 0xFF);
        stream.write(cw >> 8); // Width in chars
        stream.write(ch & 0xFF);
        stream.write(ch >> 8); // Height in chars
		stream.write(pw); // Char width
		stream.write(ph); // Char height

		stream.write(palette.length > 16 ? 8 : 4); // BPP (byte)

		if (Main.MODE != Main.Mode.COMPUTERCRAFT) {
			stream.write(16); // Palette array size
			stream.write(3);
			stream.write(0); // Palette entry size
			for (int i = 0; i < 16; i++) {
				stream.write(palette[i].getRGB() & 0xFF);
				stream.write((palette[i].getRGB() >> 8) & 0xFF);
				stream.write((palette[i].getRGB() >> 16) & 0xFF);
			}
		} else {
			stream.write(0); // Palette array size
			stream.write(0);
			stream.write(0); // Palette entry size
		}

        for (int cy = 0; cy < ch; cy++) {
            for (int cx = 0; cx < cw; cx++) {
				int[] pixels = new int[pw * ph];
				for (int py = 0; py < ph; py++) {
					for (int px = 0; px < pw; px++) {
						pixels[py * pw + px] = image.getRGB(cx * pw + px, cy * ph + py);
					}
				}

                List<Integer> pixelList = new ArrayList<>();

                for (int i = 0; i < pixels.length; i++) {
                    if (!pixelList.contains(pixels[i])) {
                        pixelList.add(pixels[i]);
                    }
                }

                if (pixelList.size() > 2) {
					double minDistance = Double.MAX_VALUE;
					int minValue1 = 0, minValue2 = 0;

					for (int i = 0; i < pixelList.size(); i++) {
						int v1 = pixelList.get(i);
						for (int j = 0; j <= i; j++) {
							// i and j are our test colours
							double dist = 0;
							int v2 = pixelList.get(j);
							for (int k = 0; k < pixels.length; k++) {
								double dist0 = Utils.getColorDistanceSq(v1, pixels[k]);
								double dist1 = Utils.getColorDistanceSq(v2, pixels[k]);
								dist += Math.min(dist0, dist1);
							}
							if (dist < minDistance) {
								minValue1 = v1;
								minValue2 = v2;
								minDistance = dist;
							}
						}
					}

					pixelList.clear();
					pixelList.add(minValue1);
					pixelList.add(minValue2);
                }

                int quadrant = 0;
                if (pixelList.size() == 2) {
                    for (int i = 0; i < pixels.length; i++) {
                        double dist0 = Utils.getColorDistance(pixelList.get(0), pixels[i]);
                        double dist1 = Utils.getColorDistance(pixelList.get(1), pixels[i]);
                        if (dist1 < dist0) {
                            quadrant |= (1 << (pw * ph - 1)) >> i;
                        }
                    }
                }

                int bgIndex = getColorIndex(palette, pixelList.get(0));
                int fgIndex = pixelList.size() == 2 ? getColorIndex(palette, pixelList.get(1)) : bgIndex;
                if (bgIndex == fgIndex) {
                    quadrant = 0;
                }

				if (Main.MODE == Main.Mode.COMPUTERCRAFT) {
					if ((quadrant & 0x01) != 0) {
						int t = fgIndex;
						fgIndex = bgIndex;
						bgIndex = t;
						quadrant ^= 0x3F;
					}
				} else if (pw * ph > 2) {
					if (bgIndex > fgIndex) {
						int t = fgIndex;
						fgIndex = bgIndex;
						bgIndex = t;
						quadrant ^= (1 << (pw * ph)) - 1;
					}
				}

				if (pw * ph == 2 && quadrant == 1) {
					int t = fgIndex;
					fgIndex = bgIndex;
					bgIndex = t;
					quadrant = 0;
				}

                if (Main.DEBUG) {
                    System.out.println(String.format("%d %d: %d/%d -> %d", cx, cy, bgIndex, fgIndex, quadrant));
                }

				if (palette.length > 2) {
					if (pw * ph == 1) {
						stream.write(fgIndex);
					} else {
						if (palette.length > 16) {
							stream.write(bgIndex);
							stream.write(fgIndex);
						} else {
							stream.write(bgIndex << 4 | fgIndex);
						}
					}

					if (pw * ph > 2) {
						stream.write(quadrant);
					}
				} else {
					stream.write(quadrant);
				}

				for (int py = 0; py < ph; py++) {
					for (int px = 0; px < pw; px++) {
						int i = (1 << (pw * ph - 1)) >> (py * pw + px);
						if ((quadrant & i) != 0) {
							output.setRGB(cx * pw + px, cy * ph + py, palette[fgIndex].getRGB());
						} else {
							output.setRGB(cx * pw + px, cy * ph + py, palette[bgIndex].getRGB());
						}
					}
				}
            }
        }

        stream.close();
		return output;
    }

    private int getColorIndex(Color[] palette, int v) {
        double maxDist = Double.POSITIVE_INFINITY;
        int ci = -1;

        for (int i = 0; i < palette.length; i++) {
            if (palette[i].getRGB() == v) {
                return i;
            }
            double d = Utils.getColorDistance(palette[i].getRGB(), v);
            if (d < maxDist) {
                ci = i;
                maxDist = d;
            }
        }

        if (ci == -1) {
            System.out.println("WARNING! INDEX NOT FOUND! " + v);
            return 0;
        }
        return ci;
    }
}
