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
}
