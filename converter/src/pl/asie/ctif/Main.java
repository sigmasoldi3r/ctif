package pl.asie.ctif;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import pl.asie.ctif.platform.Platform;
import pl.asie.ctif.platform.PlatformComputerCraft;
import pl.asie.ctif.platform.PlatformOpenComputers;

public class Main {
	private static class Parameters {
		@Parameter(names = {"-m", "--mode"}, description = "Target platform (cc, cc-paletted, oc-tier2, oc-tier3)")
		private String mode = "oc-tier3";

		@Parameter(names = {"-d", "--debug"}, description = "Enable debugging", hidden = true)
		private boolean debug = false;

		@Parameter(names = {"-W", "--width"}, description = "Output image width")
		private int w;

		@Parameter(names = {"-H", "--height"}, description = "Output image height")
		private int h;

		@Parameter(names = {"-N", "--no-aspect"}, description = "Ignore aspect ratio")
		private boolean ignoreAspectRatio = false;

		@Parameter(names = {"-o", "--output"}, description = "Output filename")
		private String outputFilename;

		@Parameter(names = {"-P", "--preview"}, description = "Preview image filename")
		private String previewFilename;

		@Parameter(description = "Input file")
		private List<String> files = new ArrayList<>();

		@Parameter(names = {"-h", "--help"}, description = "Print usage", help = true)
		private boolean help;
	}

	public static Platform PLATFORM = null;
	private static final Map<String, Platform> PLATFORMS = new HashMap<>();

	static {
		PLATFORMS.put("cc", new PlatformComputerCraft(false));
		PLATFORMS.put("cc-paletted", new PlatformComputerCraft(true));
//		PLATFORMS.put("oc-tier1", new PlatformOpenComputers(1));
		PLATFORMS.put("oc-tier2", new PlatformOpenComputers(2));
		PLATFORMS.put("oc-tier3", new PlatformOpenComputers(3));
	}

    public static boolean DEBUG = false;

	private static Parameters params;

	private static int rCeil(int x, int y) {
		if (x % y > 0) {
			return x - (x % y) + y;
		} else {
			return x;
		}
	}

	public static void main(String[] args) {
		params = new Parameters();
		JCommander jCommander = new JCommander(params, args);

		if (params.help) {
			jCommander.usage();
			System.exit(0);
		}

		for (Map.Entry<String, Platform> entry : PLATFORMS.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(params.mode)) {
				PLATFORM = entry.getValue();
				break;
			}
		}

		if (params.files.size() == 0) {
			System.err.println("No input file specified!");
			System.exit(1);
		}

		if (PLATFORM == null) {
			System.err.println(String.format("Invalid mode: %s", params.mode));
			System.exit(1);
		}

		DEBUG = params.debug;

		BufferedImage image = Utils.loadImage(params.files.get(0));
		if (image == null) {
			System.err.println(String.format("Could not load image: %s", params.files.get(0)));
			System.exit(1);
		}

		Color[] palette = PLATFORM.getPalette();
		params.w = (params.w > 0) ? rCeil(params.w, PLATFORM.getCharWidth()) : 0;
		params.h = (params.h > 0) ? rCeil(params.h, PLATFORM.getCharHeight()) : 0;

		if (params.w > 0 && params.h == 0) {
			params.h = params.w * image.getHeight() / image.getWidth();
		} else if (params.w == 0 && params.h > 0) {
			params.w = params.h * image.getWidth() / image.getHeight();
		}

		if (params.w * params.h > PLATFORM.getCharsPx()) {
			System.err.println(String.format("Size too large: %dx%d (maximum size: %d pixels)", params.w, params.h, PLATFORM.getCharsPx()));
			System.exit(1);
		} else if (params.w > PLATFORM.getWidthPx()) {
			System.err.println(String.format("Width too large: %d (maximum width: %d)", params.w, PLATFORM.getWidthPx()));
			System.exit(1);
		} else if (params.h > PLATFORM.getHeightPx()) {
			System.err.println(String.format("Height too large: %d (maximum height: %d)", params.h, PLATFORM.getHeightPx()));
			System.exit(1);
		}

		if (params.w == 0 || params.h == 0) {
			float x = (params.ignoreAspectRatio ? PLATFORM.getDefaultAspectRatio() : (float) image.getWidth() / image.getHeight());
			float y = 1.0f;
			float a = Math.min(Math.min(
					(float) PLATFORM.getWidthPx() / x,
					(float) PLATFORM.getHeightPx() / y),
					(float) Math.sqrt((float) PLATFORM.getCharsPx() / (x*y)));
			params.w = rCeil((int) Math.floor(x * a), PLATFORM.getCharWidth());
			params.h = rCeil((int) Math.floor(y * a), PLATFORM.getCharHeight());
		}

		if (PLATFORM.getCustomColorCount() > 0) {
			PaletteGenerator generator = new PaletteGenerator(PLATFORM.getCustomColorCount());
			palette = generator.generate(image, palette);
		}

		BufferedImage paletteImage = new BufferedImage(palette.length, 1, BufferedImage.TYPE_3BYTE_BGR);
		for (int i = 0; i < palette.length; i++) {
			paletteImage.setRGB(i, 0, palette[i].getRGB());
		}
		Utils.saveImage(paletteImage, "palette.png");

		int width = params.w;
		int height = params.h;

		BufferedImage resizedImage = image.getWidth() == width && image.getHeight() == height ? image : Utils.resize(image, width, height, false);
		try {
			BufferedImage inputImage = Utils.dither(resizedImage, "palette.png");
			BufferedImage outputImage = inputImage;

			CtifWriter writer = new CtifWriter();
			try {
				outputImage = writer.write(new FileOutputStream(params.outputFilename != null ? params.outputFilename : params.files.get(0) + ".ctif"), inputImage, palette);
			} catch (Exception e) {
				e.printStackTrace();
			}

			if (params.previewFilename != null) {
				Utils.saveImage(Utils.resizeBox(outputImage, width * 2, height * 2), new File(params.previewFilename).getAbsolutePath());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}