// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.
//
//  Copyright © 2021 Trenton Kress
//  This file is part of project: Darkan
//
package com.rs.cache.loaders.model;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;

public class ColorQuantizer {
	/**
	 * Maximum number of colors in an indexed image, leaving one for transparency
	 */
	public static final int MAX_INDEXED_COLORS = 255;

	private ColorQuantizer() {
		// Prevent Instantiation
	}

	/**
	 * Quantizes the image to {@link #MAX_INDEXED_COLORS} with white matte for areas
	 * with partial transparency (full transparency will be preserved).
	 * 
	 * @return {@link BufferedImage} with type
	 *         {@link BufferedImage#TYPE_BYTE_INDEXED} and quantized colors
	 */
	public static BufferedImage quantize(BufferedImage source) {
		return quantize(source, Color.WHITE);
	}

	/**
	 * Quantizes the image to {@link #MAX_INDEXED_COLORS} with the provided matte
	 * {@link Color} for areas with partial transparency (full transparency will be
	 * preserved).
	 * 
	 * @return {@link BufferedImage} with type
	 *         {@link BufferedImage#TYPE_BYTE_INDEXED} and quantized colors
	 */
	public static BufferedImage quantize(BufferedImage source, Color matteColor) {
		return quantize(source, matteColor, MAX_INDEXED_COLORS);
	}

	/**
	 * Quantizes the image to the provided number of colors with the provided matte
	 * {@link Color} for areas with partial transparency (full transparency will be
	 * preserved).
	 * 
	 * @return {@link BufferedImage} with type
	 *         {@link BufferedImage#TYPE_BYTE_INDEXED} and quantized colors
	 */
	public static BufferedImage quantize(BufferedImage source, Color matteColor, int maxColors) {
		final int width = source.getWidth();
		final int height = source.getHeight();

		// First put the matte color so that we have a sensible result
		// for images with full alpha transparencies
		final BufferedImage mattedSource = BufferedImageUtils.matte(source, matteColor);

		// Get two copies of RGB data (quantization will overwrite one)
		final int[][] bitmap = BufferedImageUtils.getRgb(mattedSource);

		// Quantize colors and shift palette by one for transparency color
		// We'll keep transparency color black for now.
		final int[] colors = Quantize.quantizeImage(bitmap, maxColors);
		final int[] colorsWithAlpha = new int[colors.length + 1];
		System.arraycopy(colors, 0, colorsWithAlpha, 1, colors.length);
		colorsWithAlpha[0] = matteColor.getRGB();
		final IndexColorModel colorModel = new IndexColorModel(8, colorsWithAlpha.length, colorsWithAlpha, 0, false, 0, DataBuffer.TYPE_BYTE);

		// Write the results to an indexed image, skipping the fully transparent bits
		final BufferedImage quantized = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, colorModel);
		final WritableRaster raster = quantized.getRaster();
		final int[][] rgb = BufferedImageUtils.getRgb(source);
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				final int value = (rgb[x][y] & 0xff000000) != 0x00000000 ? bitmap[x][y] + 1 : 0;
				raster.setPixel(x, y, new int[] { value });
			}
		}

		return quantized;
	}

	/**
	 * Reduces a direct color buffered image to an indexed color one without quality
	 * loss. To make sure no quality loss will occur, check the results of the
	 * {@link #getColorReductionInfo(BufferedImage)} method call.
	 * 
	 * @throws IllegalArgumentException if the application of this method would
	 *                                  result in image quality loss
	 */
	public static BufferedImage reduce(BufferedImage source) {
		final int width = source.getWidth();
		final int height = source.getHeight();

		if (BufferedImageUtils.hasPartialTransparency(source)) {
			throw new IllegalArgumentException("The source image cannot contain translucent areas");
		}

		final int[] colorsWithAlpha = BufferedImageUtils.getDistictColors(source, 1);
		if (colorsWithAlpha.length - 1 > MAX_INDEXED_COLORS) {
			throw new IllegalArgumentException("The source image cannot contain more than " + MAX_INDEXED_COLORS + " colors");
		}

		final IndexColorModel colorModel = new IndexColorModel(8, colorsWithAlpha.length, colorsWithAlpha, 0, false, 0, DataBuffer.TYPE_BYTE);

		// Write the results to an indexed image, skipping the fully transparent bits
		final BufferedImage quantized = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, colorModel);
		final int[][] rgb = BufferedImageUtils.getRgb(source);

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				if ((rgb[x][y] & 0xff000000) != 0x00000000) {
					quantized.setRGB(x, y, source.getRGB(x, y));
				}
			}
		}

		return quantized;
	}

	/**
	 * Returns a {@link ColorReductionInfo} for the provided image.
	 */
	public static ColorReductionInfo getColorReductionInfo(BufferedImage source) {
		return new ColorReductionInfo(BufferedImageUtils.hasPartialTransparency(source), BufferedImageUtils.countDistictColors(source));
	}

	/**
	 * Indicates how many distinct colors an image has, whether it has partial
	 * trasparency (alpha channel).
	 */
	public static class ColorReductionInfo {
		/** Number of distint colors in the image */
		public int distictColors;

		/** True if the image has partially transparent areas (alpha channel) */
		public boolean hasPartialTransparency;

		public ColorReductionInfo(boolean hasPartialTransparency, int distictColors) {
			this.hasPartialTransparency = hasPartialTransparency;
			this.distictColors = distictColors;
		}

		/**
		 * Returns true if the image can be saved in a 8-bit indexed color format with
		 * 1-bit transparency without quality loss.
		 */
		public boolean canReduceWithoutQualityLoss() {
			return !hasPartialTransparency && distictColors <= MAX_INDEXED_COLORS;
		}
	}
}
