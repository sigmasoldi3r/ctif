package pl.asie.ctif;

import pl.asie.ctif.platform.PlatformComputerCraft;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Converter {
	private final Color[] palette;
	private final BufferedImage image;
	private final float[] ditherMatrix;
	private final int ditherMatrixSize, ditherMatrixOffset;
	private final float[][] img;
	private final float[][] pal;
	private final int cw, ch, pw, ph;

	public Converter(Color[] colors, BufferedImage image, float[] ditherMatrix) {
		int i = 0;

		this.ditherMatrix = ditherMatrix;
		this.ditherMatrixSize = ditherMatrix != null ? (int) Math.sqrt(ditherMatrix.length) : 0;
		this.ditherMatrixOffset = (ditherMatrixSize - 1) / 2;

		this.image = image;
		this.palette = colors;

		this.img = new float[image.getWidth() * image.getHeight()][3];
		this.pal = new float[colors.length][3];

		this.pw = Main.PLATFORM.getCharWidth();
		this.ph = Main.PLATFORM.getCharHeight();
		this.cw = image.getWidth() / pw;
		this.ch = image.getHeight() / ph;

		for (int comp : Utils.getRGB(image)) {
			img[i++] = Utils.getYUV(comp);
		}

		for (i = 0; i < colors.length; i++) {
			pal[i] = Utils.getYUV(colors[i].getRGB());
		}
	}

    public BufferedImage write(OutputStream stream) throws IOException {
		BufferedImage output = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);

		stream.write('C');
		stream.write('T');
		stream.write('I');
		stream.write('F');

		stream.write(1); // Header version
		stream.write(0); // Platform variant (0 - default)
		stream.write(Main.PLATFORM.platformId);
		stream.write(Main.PLATFORM.platformId >> 8); // Platform ID
        stream.write(cw & 0xFF);
        stream.write(cw >> 8); // Width in chars
        stream.write(ch & 0xFF);
        stream.write(ch >> 8); // Height in chars
		stream.write(pw); // Char width
		stream.write(ph); // Char height

		stream.write(palette.length > 16 ? 8 : 4); // BPP (byte)

		if (Main.PLATFORM.getCustomColorCount() > 0) {
			stream.write(3); // Palette entry size
			stream.write(16);
			stream.write(0); // Palette array size
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

		writePixelData(stream, output);
        stream.close();
		return output;
    }

    private void addQuantError(float[][] pixelArray, int x, int y, int w, int h, float[] expected, float[] received, float mul) {
		if (x >= 0 && y >= 0 && x < w && y < h) {
			Utils.addQuantError(pixelArray[y*w+x], expected, received, mul);
		}
	}

    private void writePixelData(OutputStream stream, BufferedImage output) throws IOException {
		int ew = (pw + ditherMatrixOffset * 2);
		int eh = (ph + ditherMatrixOffset * 2);

		for (int cy = 0; cy < ch; cy++) {
			for (int cx = 0; cx < cw; cx++) {
				float[][] pixels = new float[pw * ph][];
				for (int py = 0; py < ph; py++) {
					for (int px = 0; px < pw; px++) {
						pixels[py * pw + px] = img[(cy * ph + py) * image.getWidth() + cx * pw + px];
					}
				}

				int bci1 = 0, bci2 = 0, bcq = 0;
				double bcerr = Double.MAX_VALUE;
				float[][] bcea = new float[ew * eh][3];
				float[][] tPixels = new float[pixels.length][3];
				float[][] errors = new float[ew * eh][3];

				for (int ci1 = 1; ci1 < pal.length; ci1++) {
					if (bcerr == 0) break;
					float[] col1 = pal[ci1];

					for (int ci2 = 0; ci2 < ci1; ci2++) {
						if (bcerr == 0) break;
						float[] col2 = pal[ci2];
						double cerr = 0;
						int cq = 0;

						for (int i = 0; i < pixels.length; i++) {
							tPixels[i][0] = pixels[i][0];
							tPixels[i][1] = pixels[i][1];
							tPixels[i][2] = pixels[i][2];
						}

						for (int i = 0; i < errors.length; i++) {
							errors[i][0] = 0;
							errors[i][1] = 0;
							errors[i][2] = 0;
						}

						for (int i = 0; i < tPixels.length; i++) {
							float[] col = tPixels[i];
							float[] colR = col1;
							double cerr1 = Utils.getColorDistanceSq(col, col1);
							double cerr2 = Utils.getColorDistanceSq(col, col2);
							if (cerr2 < cerr1) {
								cq |= (1 << (pw * ph - 1 - i));
								cerr += cerr2;
								colR = col2;
							} else {
								cerr += cerr1;
							}

							if (cerr >= bcerr)
								break;

							int qx = (i % pw);
							int qy = (i / pw);

							int ip = ditherMatrixSize * ditherMatrixOffset;
							for (int iy = 0; iy < ditherMatrixSize-ditherMatrixOffset; iy++) {
								for (int ix = -ditherMatrixOffset; ix < ditherMatrixSize-ditherMatrixOffset; ix++) {
									addQuantError(tPixels, qx+ix, qy+iy, pw, ph, col, colR, ditherMatrix[ip]);
									addQuantError(errors, qx+ix+ditherMatrixOffset, qy+iy+ditherMatrixOffset, ew, eh, col, colR, ditherMatrix[ip]);
									ip++;
								}
							}
						}

						if (cerr < bcerr) {
							bci1 = ci1; bci2 = ci2; bcq = cq; bcerr = cerr;
							for (int i = 0; i < errors.length; i++) {
								bcea[i][0] = errors[i][0];
								bcea[i][1] = errors[i][1];
								bcea[i][2] = errors[i][2];
							}
						}
					}
				}

				for (int iy = 0; iy < eh; iy++) {
					int ry = cy * ph + iy - ditherMatrixOffset;
					if (ry >= 0 && ry < ch*ph) {
						for (int ix = 0; ix < ew; ix++) {
							int rx = cx * pw + ix - ditherMatrixOffset;
							if (rx >= 0 && rx < cw * pw) {
								for (int i = 0; i < 3; i++) {
									img[ry * cw * pw + rx][i] += bcea[iy * ew + ix][i];
								}
							}
						}
					}
				}

				int quadrant = bcq;
				int bgIndex = bci1;
				int fgIndex = bci2;
				if (bgIndex == fgIndex) {
					quadrant = 0;
				}

				if (Main.PLATFORM instanceof PlatformComputerCraft) {
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
					System.out.println(String.format("NEW %d %d: %d/%d -> %d", cx, cy, bgIndex, fgIndex, quadrant));
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
	}
}
