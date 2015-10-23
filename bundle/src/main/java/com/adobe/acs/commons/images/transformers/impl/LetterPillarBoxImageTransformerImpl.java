/*
 * #%L
 * ACS AEM Commons Bundle
 * %%
 * Copyright (C) 2013 Adobe
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package com.adobe.acs.commons.images.transformers.impl;

import java.awt.Color;
import java.awt.Dimension;
import java.util.Map;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.acs.commons.images.ImageTransformer;
import com.day.image.Layer;

/**
 * ACS AEM Commons - Image Transformer - Letter/Piller Box ImageTransformer that
 * resizes the layer. Accepts two Integer params: height and width. If either is
 * left blank the missing dimension will be computed based on the original
 * layer's aspect ratio. If the newly resized image does not fit into the
 * original dimensions, this will create a background layer
 */
//@formatter:off
@Component
@Properties({
        @Property(
                name = ImageTransformer.PROP_TYPE,
                value = LetterPillarBoxImageTransformerImpl.TYPE
        )
})
@Service
//@formatter:on
public class LetterPillarBoxImageTransformerImpl implements ImageTransformer {
    private static final Logger log = LoggerFactory.getLogger(LetterPillarBoxImageTransformerImpl.class);

    static final String TYPE = "letter-piller-box";

    private static final String KEY_WIDTH = "width";
    private static final String KEY_WIDTH_ALIAS = "w";

    private static final String KEY_HEIGHT = "height";
    private static final String KEY_HEIGHT_ALIAS = "h";

    private static final String KEY_ALPHA = "alpha";
    private static final String KEY_ALPHA_ALIAS = "a";

    private static final String KEY_COLOR = "color";
    private static final String KEY_COLOR_ALIAS = "c";

    private static final Color TRANSPARENT = new Color(255, 255, 255, 0);

    private static final int DEFAULT_MAX_DIMENSION = 50000;
    private int maxDimension = DEFAULT_MAX_DIMENSION;
    @Property(label = "Max dimension in px",
            description = "Maximum size height and width can be re-sized to. [ Default: 50000 ]",
            intValue = DEFAULT_MAX_DIMENSION)
    public static final String PROP_MAX_DIMENSION = "max-dimension";

    @Override
    public final Layer transform(final Layer layer, final ValueMap properties) {

        if ((properties == null) || properties.isEmpty()) {
            log.warn("Transform [ {} ] requires parameters.", TYPE);
            return layer;
        }

        log.debug("Transforming with [ {} ]", TYPE);

        Dimension newSize = getResizeDimensions(properties, layer);
        Color color = getColor(properties);
        Layer resized = resize(layer, newSize);

        Layer result = build(newSize, resized, color);

        return result;
    }

    /*
     * Creates the actual piller/letter boxing.
     */
    private Layer build(Dimension size, Layer img, Color color) {

        Layer merged = new Layer(size.width, size.height, color);

        int startXpos = 0;
        int startYpos = 0;
        if (img.getHeight() == size.height) {
            // Pillar
            startXpos = calculateStartPosition(size.width, img.getWidth());
        } else if (img.getWidth() == size.width) {
            // Letter
            startYpos = calculateStartPosition(size.height, img.getHeight());
        }

        merged.blit(img, startXpos, startYpos, img.getWidth(), img.getHeight(), 0, 0);
        return merged;
    }

    /*
     * Resizes the layer but keeps original aspect ratio. Thus preparing it for
     * the boxing.
     */
    private Layer resize(Layer original, Dimension newDimensions) {

        final Dimension origDimensions = new Dimension(original.getWidth(), original.getHeight());
        final int fixedDimension = getFixedDimension(origDimensions, newDimensions);
        int newWidth = newDimensions.width;
        int newHeight = newDimensions.height;

        if (fixedDimension < 0) {

            // Height is "fixed", calculate width
            newWidth = (origDimensions.width * newDimensions.height) / origDimensions.height;
        } else if (fixedDimension > 0) {

            // Width is "fixed", calculate height
            newHeight = (newDimensions.width * origDimensions.height) / origDimensions.width;
        }

        original.resize(newWidth, newHeight);
        return original;
    }

    /*
     * Calculates whether width or height is being used for resize basis.
     *
     * Returns an indicator value on which dimension is the "fixed" dimension
     *
     * Zero if the aspect ratios are the same Negative if the width should be
     * fixed Positive if the height should be fixed
     *
     * @param start the dimensions of the original image
     *
     * @param end the dimensions of the final image
     *
     * @return a value indicating which dimension is fixed
     */
    private int getFixedDimension(Dimension start, Dimension end) {
        double startRatio = start.getWidth() / start.getHeight();
        double finalRatio = end.getWidth() / end.getHeight();
        return Double.compare(startRatio, finalRatio);
    }

    private Dimension getResizeDimensions(final ValueMap properties, final Layer layer) {

        int width = properties.get(KEY_WIDTH, properties.get(KEY_WIDTH_ALIAS, 0));
        int height = properties.get(KEY_HEIGHT, properties.get(KEY_HEIGHT_ALIAS, 0));

        if (width > maxDimension) {
            width = maxDimension;
        }

        if (height > maxDimension) {
            height = maxDimension;
        }

        if ((width < 1) && (height < 1)) {
            width = layer.getWidth();
            height = layer.getHeight();
        } else if (width < 1) {
            final float aspect = (float) height / layer.getHeight();
            width = Math.round(layer.getWidth() * aspect);
        } else if (height < 1) {
            final float aspect = (float) width / layer.getWidth();
            height = Math.round(layer.getHeight() * aspect);
        }
        return new Dimension(width, height);
    }

    private Color getColor(final ValueMap properties) {
        String hexcolor = properties.get(KEY_COLOR, properties.get(KEY_COLOR_ALIAS, String.class));
        float alpha = normalizeAlpha(properties.get(KEY_ALPHA, properties.get(KEY_ALPHA_ALIAS, 0.0)).floatValue());

        Color color = null;
        if (hexcolor != null) {
            try {
                Color parsed = Color.decode("0x" + hexcolor);
                color = new Color(parsed.getRed(), parsed.getGreen(), parsed.getBlue(), alpha);
            } catch (NumberFormatException ex) {
                log.warn("Invalid hex color specified: {}", hexcolor);
                color = TRANSPARENT;
            }
        }
        return color;
    }

    private float normalizeAlpha(float alpha) {
        if (alpha > 1) {
            alpha = 1f;
        } else if (alpha < 0) {
            alpha = 0f;
        }

        return alpha;
    }

    private int calculateStartPosition(int originalSize, int newSize) {
        int diff = originalSize - newSize;
        int start = diff / 2;
        return start;
    }

    @Activate
    protected final void activate(final Map<String, String> config) {
        maxDimension = PropertiesUtil.toInteger(config.get(PROP_MAX_DIMENSION), DEFAULT_MAX_DIMENSION);
    }

}
