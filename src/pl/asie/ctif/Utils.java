package pl.asie.ctif;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;

import org.im4java.core.ConvertCmd;
import org.im4java.core.IMOperation;
import org.im4java.core.Stream2BufferedImage;

import javax.imageio.ImageIO;

public final class Utils {
    private Utils() {

    }

	public static Color[] getPalette(Main.Mode mode) {
		switch (mode) {
			case COMPUTERCRAFT:
				return getCCPalette();
			case OC_TIER_2:
				return getOCPalette(2);
			case OC_TIER_3:
				return getOCPalette(3);
		}
		return new Color[0];
	}

	public static BufferedImage resize(BufferedImage image, int width, int height, boolean keepAspectRatio) {
		ConvertCmd cmd = new ConvertCmd();
		IMOperation op = new IMOperation();
		op.addImage();
		op.colorspace("YIQ");
		op.filter("Lanczos");
		if (keepAspectRatio) {
			op.resize(width, height);
		} else {
			op.resize(width, height, '!');
		}
		op.addImage("png:-");

		Stream2BufferedImage s2b = new Stream2BufferedImage();
		cmd.setOutputConsumer(s2b);
		try {
			cmd.run(op, image);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return s2b.getImage();
	}

	public static BufferedImage resizeBox(BufferedImage image, int width, int height) {
		ConvertCmd cmd = new ConvertCmd();
		IMOperation op = new IMOperation();
		op.addImage();
		op.filter("Box");
		op.resize(width, height, '!');
		op.addImage("png:-");

		Stream2BufferedImage s2b = new Stream2BufferedImage();
		cmd.setOutputConsumer(s2b);
		try {
			cmd.run(op, image);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return s2b.getImage();
	}

	public static void saveImage(BufferedImage image, String location) {
		ConvertCmd cmd = new ConvertCmd();
		IMOperation op = new IMOperation();
		op.addImage();
		op.addImage(location);

		try {
			cmd.run(op, image);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static BufferedImage loadImage(String location) {
		try {
			if (location.equals("-")) {
				return ImageIO.read(System.in);
			} else {
				return ImageIO.read(new File(location));
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private static Color[] getOCPalette(int tier) {
		Color[] colors = new Color[tier == 3 ? 256 : 16];
		for (int i = 0; i < 16; i++) {
			colors[i] = new Color(17 * i, 17 * i, 17 * i);
		}
		if (tier == 3) {
			for (int i = 0; i < 240; i++) {
				colors[i + 16] = new Color(
					((i / 40) % 6) * 255 / 5,
					((i / 5) % 8) * 255 / 7,
					(i % 5) * 255 / 4
				);
			}
		}
		return colors;
	}

    public static BufferedImage colorRemap(BufferedImage image, int max) {
        ConvertCmd cmd = new ConvertCmd();
        IMOperation op = new IMOperation();
        op.addImage();
		op.quantize("YIQ");
		op.colors(max);
		op.addImage("png:-");

        Stream2BufferedImage s2b = new Stream2BufferedImage();
        cmd.setOutputConsumer(s2b);
        try {
            cmd.run(op, image);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return s2b.getImage();
    }

    public static int[] getRGB(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}

	public static float[] getYUV(int c1) {
		float r1 = ((c1 >> 16) & 0xFF) / 255.0f;
		float g1 = ((c1 >> 8) & 0xFF) / 255.0f;
		float b1 = (c1 & 0xFF) / 255.0f;

		// YUV
		/* float y1 = 0.299f * r1 + 0.587f * g1 + 0.114f * b1;
		float u1 = -0.147f * r1 - 0.289f * g1 + 0.436f * b1;
		float v1 = 0.615f * r1 - 0.515f * g1 - 0.100f * b1; */

		// YIQ
		float y1 = 0.299f * r1 + 0.587f * g1 + 0.114f * b1;
		float u1 = 0.596f * r1 - 0.274f * g1 - 0.322f * b1;
		float v1 = 0.211f * r1 - 0.523f * g1 + 0.312f * b1;

		// weighted RGB
		/* float y1 = (float) Math.sqrt(2 + (r1 / 255)) * r1;
		float u1 = 2 * g1;
		float v1 = (float) Math.sqrt(3 - (r1 / 255)) * b1; */

		return new float[]{y1, u1, v1};
	}

    public static double getColorDistance(int c1, int c2) {
		return Math.sqrt(getColorDistanceSq(c1, c2));
    }

	public static double getColorDistanceSq(int c1, int c2) {
		float[] f1 = getYUV(c1);
		float[] f2 = getYUV(c2);

		return (f1[0] - f2[0]) * (f1[0] - f2[0]) +
				(f1[1] - f2[1]) * (f1[1] - f2[1]) +
				(f1[2] - f2[2]) * (f1[2] - f2[2]);
	}

	public static BufferedImage dither(BufferedImage image, int[] palette) {
		// First, convert the image to target colorspace
		int[] imageRGB = getRGB(image);
		float[][] img = new float[imageRGB.length][3];
		for (int i = 0; i < imageRGB.length; i++) {
			img[i] = getYUV(imageRGB[i]);
		}

		return image;
	}

	public static BufferedImage dither(BufferedImage image, String paletteImage) {
		ConvertCmd cmd = new ConvertCmd();
		IMOperation op = new IMOperation();
		op.addImage();
		//op.dither("None");
		op.colorspace("YIQ");
		op.dither("FloydSteinberg");
		op.remap(paletteImage);
		op.addImage("png:-");

		Stream2BufferedImage s2b = new Stream2BufferedImage();
		cmd.setOutputConsumer(s2b);
		try {
			cmd.run(op, image);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return s2b.getImage();
	}

	private static Color[] getCCPalette() {
		Color[] colors = new Color[16];
		colors[0] = new Color(0xf0f0f0);
		colors[1] = new Color(0xf2b233);
		colors[2] = new Color(0xe57fd8);
		colors[3] = new Color(0x99b2f2);
		colors[4] = new Color(0xdede6c);
		colors[5] = new Color(0x7fcc19);
		colors[6] = new Color(0xf2b2cc);
		colors[7] = new Color(0x4c4c4c);
		colors[8] = new Color(0x999999);
		colors[9] = new Color(0x4c99b2);
		colors[10] = new Color(0xb266e5);
		colors[11] = new Color(0x3366cc);
		colors[12] = new Color(0x7f664c);
		colors[13] = new Color(0x57a64e);
		colors[14] = new Color(0xcc4c4c);
		colors[15] = new Color(0x0C0C0C);
		return colors;
	}
}
