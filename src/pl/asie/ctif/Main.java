package pl.asie.ctif;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class Main {
	private static class Parameters {
		@Parameter(names = {"-m", "--mode"}, description = "Target platform (cc, oc, oc-tier2)")
		private String mode = "oc";

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

	public enum Mode {
		COMPUTERCRAFT("cc", 2, 3, 164 * 2, 81 * 3, 2),
		OC_TIER_3("oc", 2, 4, 320, 200, 1),
		OC_TIER_2("oc-tier2", 2, 4, 160, 100, 1);

		public final String key;
		public final int pw, ph, dw, dh;
		public final int platformId;

		Mode(String name, int pw, int ph, int dw, int dh, int platformId) {
			this.key = name;
			this.pw = pw;
			this.ph = ph;
			this.dw = dw;
			this.dh = dh;
			this.platformId = platformId;
		}
	}

    public static boolean DEBUG = false;
	public static Mode MODE = null;

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

		for (Mode mode : Mode.values()) {
			if (mode.key.equalsIgnoreCase(params.mode)) {
				MODE = mode;
				break;
			}
		}

		if (params.files.size() == 0) {
			System.err.println("No input file specified!");
			System.exit(1);
		}

		if (MODE == null) {
			System.err.println(String.format("Invalid mode: %s", params.mode));
			System.exit(1);
		}

		int width = MODE.dw, height = MODE.dh;
		Color[] palette = Utils.getPalette(MODE);

		if (params.w > width && params.h > height) {
			System.err.println(String.format("Size too large: %dx%d (maximum size: %dx%d)", params.w, params.h, width, height));
			System.exit(1);
		} else if (params.w > width) {
			System.err.println(String.format("Width too large: %d (maximum size: %dx%d)", params.w, width, height));
			System.exit(1);
		} else if (params.h > height) {
			System.err.println(String.format("Height too large: %d (maximum size: %dx%d)", params.h, width, height));
			System.exit(1);
		}

		if (params.w > 0) width = rCeil(params.w, MODE.pw);
		if (params.h > 0) height = rCeil(params.h, MODE.ph);

		DEBUG = params.debug;

		BufferedImage image = Utils.loadImage(params.files.get(0));

		// 300, 200; 200, 300
		// height*image.getWidth()/image.getHeight()

		if (!params.ignoreAspectRatio) {
			float aspectScreen = (float) width / height;
			float aspectImage = (float) image.getWidth() / image.getHeight();
			if (aspectScreen >= aspectImage) {
				width = rCeil((int) Math.floor((float) height * aspectImage), MODE.pw);
			} else {
				height = rCeil((int) Math.floor((float) width / aspectImage), MODE.ph);
			}
		}

		if (MODE == Mode.OC_TIER_2 || MODE == Mode.OC_TIER_3) {
			PaletteGenerator generator = new PaletteGenerator();
			palette = generator.generate(image, palette);
		}

		BufferedImage paletteImage = new BufferedImage(palette.length, 1, BufferedImage.TYPE_3BYTE_BGR);
		for (int i = 0; i < palette.length; i++) {
			paletteImage.setRGB(i, 0, palette[i].getRGB());
		}
		Utils.saveImage(paletteImage, "palette.png");

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