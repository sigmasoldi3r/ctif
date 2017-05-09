package pl.asie.ctif;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

import com.mortennobel.imagescaling.ResampleFilters;
import com.mortennobel.imagescaling.ResampleOp;
import pl.asie.ctif.colorspace.Colorspace;

import javax.imageio.ImageIO;

public final class Utils {
    private Utils() {

    }

	public static BufferedImage resize(BufferedImage image, int width, int height) {
    	ResampleOp op = new ResampleOp(width, height);
    	op.setFilter(ResampleFilters.getLanczos3Filter());
		return op.filter(image, null);
	}

	public static BufferedImage resizeBox(BufferedImage image, int width, int height) {
		ResampleOp op = new ResampleOp(width, height);
		op.setFilter(ResampleFilters.getBoxFilter());
		return op.filter(image, null);
	}

	public static void saveImage(BufferedImage image, String location) {
    	try {
			ImageIO.write(image, "png", new File(location));
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

    public static int[] getRGB(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
	}

	public static void addQuantError(float[] target, float[] expected, float[] received, float mul) {
    	if (mul != 0.0f) {
			for (int i = 0; i < target.length; i++) {
				target[i] += (expected[i] - received[i]) * mul;
			}
		}
	}

    public static double getColorDistance(int c1, int c2, Colorspace colorspace) {
		return Math.sqrt(getColorDistanceSq(c1, c2, colorspace));
    }

	public static double getColorDistanceSq(int c1, int c2, Colorspace colorspace) {
    	return getColorDistanceSq(colorspace.fromRGB(c1), colorspace.fromRGB(c2));
	}

	public static double getColorDistanceSq(float[] f1, float[] f2) {
		return (f1[0] - f2[0]) * (f1[0] - f2[0]) +
				(f1[1] - f2[1]) * (f1[1] - f2[1]) +
				(f1[2] - f2[2]) * (f1[2] - f2[2]);
	}
}
