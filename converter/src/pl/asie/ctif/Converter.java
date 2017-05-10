package pl.asie.ctif;

import pl.asie.ctif.platform.PlatformComputerCraft;
import pl.asie.ctif.platform.PlatformOpenComputers;
import pl.asie.ctif.platform.PlatformZXSpectrum;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;

public class Converter {
	public enum DitherMode {
		NONE,
		ERROR,
		ORDERED
	};

	private final Color[] palette;
	private final BufferedImage image;
	private final DitherMode ditherMode;
	private final float[] ditherMatrix;
	private final int ditherMatrixSize, ditherMatrixOffset;
	private final float[][] img;
	private final float[][] pal;
	private final int cw, ch, pw, ph;
	private final int ditherMax;

	public Converter(Color[] colors, BufferedImage image, DitherMode ditherMode, float[] ditherMatrix) {
		int i = 0;

		this.ditherMode = ditherMode;
		this.ditherMatrix = ditherMatrix;
		if (ditherMode == DitherMode.ORDERED) {
			assert ditherMatrix != null;
			this.ditherMatrixSize = (int) Math.sqrt(ditherMatrix.length - 1);
			this.ditherMatrixOffset = 0;
			this.ditherMax = (int) ditherMatrix[ditherMatrix.length - 1];
		} else {
			this.ditherMatrixSize = ditherMatrix != null ? (int) Math.sqrt(ditherMatrix.length) : 0;
			this.ditherMatrixOffset = (ditherMatrixSize - 1) / 2;
			this.ditherMax = 0;
		}

		this.image = image;
		this.palette = colors;

		this.img = new float[image.getWidth() * image.getHeight()][3];
		this.pal = new float[colors.length][3];

		this.pw = Main.PLATFORM.getCharWidth();
		this.ph = Main.PLATFORM.getCharHeight();
		this.cw = image.getWidth() / pw;
		this.ch = image.getHeight() / ph;

		for (i = 0; i < img.length; i++) {
			img[i] = Main.COLORSPACE.fromRGB(image.getRGB(i % image.getWidth(), i / image.getWidth()));
		}

		for (i = 0; i < colors.length; i++) {
			pal[i] = Main.COLORSPACE.fromRGB(colors[i].getRGB());
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

		int quadrantLen = (pw * ph + 7) / 8;

		float[][] pixels = new float[pw * ph][];
		float[][] bcea = new float[ew * eh][3];
		float[][] tPixels = new float[pixels.length][3];
		float[][] errors = new float[ew * eh][3];
		int[] bcq = new int[quadrantLen];
		int[] cq = new int[quadrantLen];

		float[] colA = new float[3];

		for (int cy = 0; cy < ch; cy++) {
			for (int cx = 0; cx < cw; cx++) {
				for (int py = 0; py < ph; py++) {
					for (int px = 0; px < pw; px++) {
						pixels[py * pw + px] = img[(cy * ph + py) * image.getWidth() + cx * pw + px];
					}
				}

				int bci1 = 0, bci2 = 0;
				double bcerr = Double.MAX_VALUE;

				for (int ci1 = 1; ci1 < pal.length; ci1++) {
					if (bcerr == 0) break;
					float[] col1 = pal[ci1];

					for (int ci2 = (Main.PLATFORM instanceof PlatformZXSpectrum) ? (ci1 >= 8 ? 8 : 0) : 0; ci2 < ci1; ci2++) {
						if (bcerr == 0) break;
						float[] col2 = pal[ci2];
						double cerr = 0;

						for (int i = 0; i < quadrantLen; i++) {
							cq[i] = 0;
						}

						if (ditherMode == DitherMode.NONE) {
							for (int i = 0; i < pixels.length; i++) {
								float[] col = pixels[i];
								double cerr1 = Utils.getColorDistanceSq(col, col1);
								double cerr2 = Utils.getColorDistanceSq(col, col2);
								if (cerr2 < cerr1) {
									int pos = (pw * ph - 1 - i);
									cq[pos >> 3] |= (1 << (pos & 7));
									cerr += cerr2;
								} else {
									cerr += cerr1;
								}

								if (cerr >= bcerr)
									break;
							}
						} else if (ditherMode == DitherMode.ERROR) {
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
								float[] colR;
								double cerr1 = Utils.getColorDistanceSq(col, col1);
								double cerr2 = Utils.getColorDistanceSq(col, col2);
								if (cerr2 < cerr1) {
									int pos = (pw * ph - 1 - i);
									cq[pos >> 3] |= (1 << (pos & 7));
									cerr += cerr2;
									colR = col2;
								} else {
									cerr += cerr1;
									colR = col1;
								}

								if (cerr >= bcerr)
									break;

								int qx = (i % pw);
								int qy = (i / pw);

								int ip = ditherMatrixSize * ditherMatrixOffset;
								for (int iy = 0; iy < ditherMatrixSize - ditherMatrixOffset; iy++) {
									for (int ix = -ditherMatrixOffset; ix < ditherMatrixSize - ditherMatrixOffset; ix++) {
										addQuantError(tPixels, qx + ix, qy + iy, pw, ph, col, colR, ditherMatrix[ip]);
										addQuantError(errors, qx + ix + ditherMatrixOffset, qy + iy + ditherMatrixOffset, ew, eh, col, colR, ditherMatrix[ip]);
										ip++;
									}
								}
							}
						} else {
							// http://bisqwit.iki.fi/story/howto/dither/jy/

							cerr += Utils.getColorDistanceSq(col1, col2) * 0.1 * pixels.length;

							for (int i = 0; i < pixels.length; i++) {
								float[] col = pixels[i];
								int qx = (i % pw);
								int qy = (i / pw);

								float jf =
										(col[0]*col1[0] - col[0]*col2[0] - col1[0]*col2[0] + col2[0]*col2[0] +
										 col[1]*col1[1] - col[1]*col2[1] - col1[1]*col2[1] + col2[1]*col2[1] +
									 	 col[2]*col1[2] - col[2]*col2[2] - col1[2]*col2[2] + col2[2]*col2[2]) /
										((col1[0]-col2[0])*(col1[0]-col2[0]) +
										 (col1[1]-col2[1])*(col1[1]-col2[1]) +
										 (col1[2]-col2[2])*(col1[2]-col2[2]));
								int birat = ditherMax - Math.round(jf * ditherMax);
								if (birat < 0) birat = 0;
								else if (birat > ditherMax) birat = ditherMax;

								colA[0] = (col2[0] * birat + col1[0] * (ditherMax - birat)) / ditherMax;
								colA[1] = (col2[1] * birat + col1[1] * (ditherMax - birat)) / ditherMax;
								colA[2] = (col2[2] * birat + col1[2] * (ditherMax - birat)) / ditherMax;
								cerr += Utils.getColorDistanceSq(col, colA);

								if (cerr >= bcerr)
									break;

								int threshold = (int) ditherMatrix[((cy * ph + qy) % ditherMatrixSize) * ditherMatrixSize + ((cx * pw + qx) % ditherMatrixSize)];
								if (threshold < birat) {
									int pos = (pw * ph - 1 - i);
									cq[pos >> 3] |= (1 << (pos & 7));
								}
							}
						}

						if (cerr < bcerr) {
							bci1 = ci1;
							bci2 = ci2;
							bcerr = cerr;
							if (ditherMode == DitherMode.ERROR) {
								for (int i = 0; i < errors.length; i++) {
									bcea[i][0] = errors[i][0];
									bcea[i][1] = errors[i][1];
									bcea[i][2] = errors[i][2];
								}
							}
							for (int i = 0; i < quadrantLen; i++) {
								bcq[i] = cq[i];
							}
						}
					}
				}

				if (ditherMode == DitherMode.ERROR) {
					for (int iy = 0; iy < eh; iy++) {
						int ry = cy * ph + iy - ditherMatrixOffset;
						if (ry >= 0 && ry < ch * ph) {
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
				}

				int[] quadrant = bcq;
				int bgIndex = bci1;
				int fgIndex = bci2;
				if (bgIndex == fgIndex) {
					for (int i = 0; i < quadrantLen; i++) quadrant[i] = 0;
				}

				if (Main.PLATFORM instanceof PlatformComputerCraft) {
					if ((quadrant[0] & 0x01) != 0) {
						int t = fgIndex;
						fgIndex = bgIndex;
						bgIndex = t;
						quadrant[0] ^= 0x3F;
					}
				} else if (Main.PLATFORM instanceof PlatformOpenComputers && pw * ph > 2) {
					if (bgIndex > fgIndex) {
						int t = fgIndex;
						fgIndex = bgIndex;
						bgIndex = t;
						quadrant[0] ^= (1 << (pw * ph)) - 1;
					}
				}

				if (pw * ph == 2 && quadrant[0] == 1) {
					int t = fgIndex;
					fgIndex = bgIndex;
					bgIndex = t;
					quadrant[0] = 0;
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
						for (int i = 0; i < quadrantLen; i++)
							stream.write(quadrant[i]);
					}
				} else {
					for (int i = 0; i < quadrantLen; i++)
						stream.write(quadrant[i]);
				}

				for (int py = 0; py < ph; py++) {
					for (int px = 0; px < pw; px++) {
						int i = (pw * ph - 1) - (py * pw + px);

						if ((quadrant[i >> 3] & (1 << (i & 7))) != 0) {
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
