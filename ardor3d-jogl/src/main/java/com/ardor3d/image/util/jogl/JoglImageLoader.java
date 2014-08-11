/**
 * Copyright (c) 2008-2010 Ardor Labs, Inc.
 *
 * This file is part of Ardor3D.
 *
 * Ardor3D is free software: you can redistribute it and/or modify it 
 * under the terms of its license which may be found in the accompanying
 * LICENSE file or at <http://www.ardor3d.com/LICENSE>.
 */

package com.ardor3d.image.util.jogl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import com.ardor3d.framework.jogl.CapsUtil;
import com.ardor3d.image.Image;
import com.ardor3d.image.PixelDataType;
import com.ardor3d.image.util.ImageLoader;
import com.ardor3d.image.util.ImageLoaderUtil;
import com.ardor3d.scene.state.jogl.util.JoglTextureUtil;
import com.ardor3d.util.geom.BufferUtils;
import com.jogamp.common.nio.Buffers;
import com.jogamp.common.os.Platform;
import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;

public class JoglImageLoader implements ImageLoader {

    public static boolean createOnHeap = false;

    protected final CapsUtil _capsUtil;

    /**
     * Flag indicating whether the mipmaps are produced by JOGL (retrieved from the file or generated)
     */
    private boolean _mipmapsProductionEnabled;

    private enum TYPE {
        BYTE(ByteBuffer.class), SHORT(ShortBuffer.class), CHAR(CharBuffer.class), INT(IntBuffer.class), FLOAT(
                FloatBuffer.class), LONG(LongBuffer.class), DOUBLE(DoubleBuffer.class);

        private final Class<? extends Buffer> bufferClass;

        private TYPE(final Class<? extends Buffer> bufferClass) {
            this.bufferClass = bufferClass;
        }
    };

    private static final String[] _supportedFormats = computeSupportedFormats();

    private static final String[] computeSupportedFormats() {
        final List<String> supportedFormatsList = new ArrayList<String>();
        if (Platform.AWT_AVAILABLE) {
            supportedFormatsList.add("." + TextureIO.GIF.toUpperCase());
        }
        supportedFormatsList.add("." + TextureIO.DDS.toUpperCase());
        supportedFormatsList.add("." + TextureIO.JPG.toUpperCase());
        supportedFormatsList.add("." + TextureIO.PNG.toUpperCase());
        supportedFormatsList.add("." + TextureIO.SGI.toUpperCase());
        supportedFormatsList.add("." + TextureIO.SGI_RGB.toUpperCase());
        return supportedFormatsList.toArray(new String[supportedFormatsList.size()]);
    }

    public static String[] getSupportedFormats() {
        return _supportedFormats;
    }

    public static void registerLoader() {
        registerLoader(new JoglImageLoader(), _supportedFormats);
        registerLoader(new JoglTgaImageLoader(), JoglTgaImageLoader.getSupportedFormats());
    }

    public static void registerLoader(final JoglImageLoader joglImageLoader, final String[] supportedFormats) {
        ImageLoaderUtil.registerHandler(joglImageLoader, supportedFormats);
    }

    public JoglImageLoader() {
        this(new CapsUtil());
    }

    public JoglImageLoader(final CapsUtil capsUtil) {
        _capsUtil = capsUtil;
    }

    public Image makeArdor3dImage(final TextureData textureData, final boolean verticalFlipNeeded) {
        final Buffer textureDataBuffer = textureData.getBuffer();
        final Image ardorImage = new Image();
        TYPE bufferDataType = getBufferDataType(textureDataBuffer);
        if (bufferDataType == null) {
            throw new UnsupportedOperationException("Unknown buffer type " + textureDataBuffer.getClass().getName());
        } else {
            int dataSizeInBytes = textureDataBuffer.capacity() * Buffers.sizeOfBufferElem(textureDataBuffer);
            ByteBuffer scratch = createOnHeap ? BufferUtils.createByteBufferOnHeap(dataSizeInBytes) : Buffers
                    .newDirectByteBuffer(dataSizeInBytes);
            if (verticalFlipNeeded) {
                flipImageData(textureDataBuffer, scratch, dataSizeInBytes, bufferDataType, textureData.getWidth(),
                        textureData.getHeight());
            } else {
                copyImageData(textureDataBuffer, scratch, bufferDataType);
            }
            ardorImage.setWidth(textureData.getWidth());
            ardorImage.setHeight(textureData.getHeight());
            ardorImage.setData(scratch);
            ardorImage.setDataFormat(JoglTextureUtil.getImageDataFormat(textureData.getPixelFormat()));
            ardorImage.setDataType(JoglTextureUtil.getPixelDataType(textureData.getPixelType()));
            ardorImage.setDataType(PixelDataType.UnsignedByte);
            if (textureData.getMipmapData() != null) {
                for (final Buffer mipmapData : textureData.getMipmapData()) {
                    dataSizeInBytes = mipmapData.capacity() * Buffers.sizeOfBufferElem(mipmapData);
                    scratch = createOnHeap ? BufferUtils.createByteBufferOnHeap(dataSizeInBytes) : Buffers
                            .newDirectByteBuffer(dataSizeInBytes);
                    bufferDataType = getBufferDataType(mipmapData);
                    if (verticalFlipNeeded) {
                        flipImageData(mipmapData, scratch, dataSizeInBytes, bufferDataType, textureData.getWidth(),
                                textureData.getHeight());
                    } else {
                        copyImageData(mipmapData, scratch, bufferDataType);
                    }
                    ardorImage.addData(scratch);
                }
            }
            return ardorImage;
        }
    }

    @Override
    public Image load(final InputStream is, final boolean verticalFlipNeeded) throws IOException {
        final String fileSuffix = getFileSuffix(is);
        final TextureData textureData = TextureIO.newTextureData(_capsUtil.getProfile(), is, _mipmapsProductionEnabled,
                fileSuffix);
        if (textureData == null) {
            return null;
        }
        return makeArdor3dImage(textureData, textureData.getMustFlipVertically() == verticalFlipNeeded);
    }

    /**
     * Returns the hypothetical file suffix by looking at the first bytes
     * 
     * @param is
     *            data source
     * @return file suffix of the data source
     * @throws IOException
     */
    protected String getFileSuffix(final InputStream is) throws IOException {
        if (is.markSupported() && is.available() >= 16) {
            is.mark(32);
            try {
                final byte[] b = new byte[32];
                is.read(b);
                if ((b[0] == 0xff && b[1] == 0xd8) || (b[0] == -1 && b[1] == -40)
                        || (b[0] == 0x4A && b[1] == 0x46 && b[2] == 0x49 && b[3] == 0x46)
                        || (b[0] == 0x45 && b[1] == 0x78 && b[2] == 0x69 && b[3] == 0x66)) {
                    return TextureIO.JPG;
                }
                /**
                 * Apache Commons Imaging and JOGL (jogamp.opengl.util.png.PngHelperInternal.getPngIdSignature()) don't
                 * use the same signature for PNG files
                 */
                if ((b[0] == 0x89 || b[0] == -119) && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' && b[4] == '\r'
                        && b[5] == '\n' && b[6] == 0x1A && b[7] == '\n') {
                    return TextureIO.PNG;
                }
                // icns
                if (b[0] == 0x69 && b[1] == 0x63 && b[2] == 0x6e && b[3] == 0x73) {
                    // Apple Icon Image
                    return "icns";
                }
                // GIF87a or GIF89a
                if (b[0] == 0x47 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x38 && (b[4] == 0x37 || b[4] == 0x39)
                        && b[5] == 0x61) {
                    return TextureIO.GIF;
                }
                // BM
                if (b[0] == 0x42 && b[1] == 0x4d) {
                    return "bmp";
                }
                if (b[0] == 0x3A && b[1] == 0xDE && b[2] == 0x68 && b[3] == 0xB1) {
                    return "dcx";
                }
                if (b[0] == 0x0A && b[1] == 0x05 && b[2] == 0x01 && b[3] == 0x08) {
                    return "pcx";
                }
                if (b[0] == 0x50 && (b[1] == 0x33 || b[1] == 0x36)) {
                    return TextureIO.PPM;
                }
                if (b[0] == 0x38 && b[1] == 0x42 && b[2] == 0x50 && b[3] == 0x53 && b[4] == 0x00 && b[5] == 0x01
                        && b[6] == 0x00 && b[7] == 0x00 && b[8] == 0x00 && b[9] == 0x00) {
                    // Adobe PhotoShop
                    return "psd";
                }
                if (b[0] == 0x49 && b[1] == 0x49 && b[2] == 0x2A && b[3] == 0x00) {
                    return TextureIO.TIFF;
                }
                if (b[0] == 0x01 && b[1] == 0xDA && b[2] == 0x01 && b[3] == 0x01 && b[4] == 0x00 && b[5] == 0x03) {
                    return TextureIO.SGI_RGB;
                }
                if (b[0] == 0x20 && b[1] == 0x53 && b[2] == 0x44 && b[3] == 0x44) {
                    return TextureIO.DDS;
                }
                if (b[0] == 0x50 && b[1] == 0x37) {
                    return TextureIO.PAM;
                }
                if (b[0] == 0x50 && (b[1] == 0x32 || b[1] == 0x35)) {
                    return "pgm";
                }
                if (b[0] == 0x50 && (b[1] == 0x31 || b[1] == 0x34)) {
                    return "pbm";
                }
                if (b[0] == 0x3D && b[1] == 0x02) {
                    return "3d2";
                }
                if (b[0] == 0x33 && b[1] == 0x44 && b[2] == 0x4D && b[3] == 0x46) {
                    return "3dmf";
                }
                if (b[0] == 0x2A && b[1] == 0x2A && b[2] == 0x54 && b[3] == 0x49 && b[4] == 0x39 && b[5] == 0x32
                        && b[6] == 0x2A && b[7] == 0x2A && b[8] == 0x01 && b[9] == 0x00 && b[10] == 0x58
                        && b[11] == 0x6E && b[12] == 0x56 && b[13] == 0x69) {
                    return "92i";
                }
                if (b[0] == 0x41 && b[1] == 0x4D && b[2] == 0x46 && b[3] == 0x46) {
                    return "amff";
                }
                if (b[0] == 0x4A && b[1] == 0x47 && (b[2] == 0x03 || b[2] == 0x04) && b[3] == 0x0E && b[4] == 0x00
                        && b[5] == 0x00 && b[6] == 0x00) {
                    return "art";
                }
                if (b[0] == 0x73 && b[1] == 0x72 && b[2] == 0x63 && b[3] == 0x64 && b[4] == 0x6F && b[5] == 0x63
                        && b[6] == 0x69 && b[7] == 0x64 && b[8] == 0x3A) {
                    return "cals";
                }
                if (b[0] == 0x07 && b[1] == 0x20 && b[2] == 0x4D && b[3] == 0x4D) {
                    return "cam";
                }
                if (b[0] == 0x20 && b[1] == 0x77 && b[2] == 0x00 && b[3] == 0x02) {
                    return "cbd";
                }
                if (b[0] == 0x45 && b[1] == 0x59 && b[2] == 0x45 && b[3] == 0x53) {
                    return "ce2";
                }
                if (b[0] == 0x80 && b[1] == 0x2A && b[2] == 0x5F && b[3] == 0xD7 && b[4] == 0x00 && b[5] == 0x00
                        && b[6] == 0x08 && b[7] == 0x00 && b[8] == 0x00 && b[9] == 0x00 && b[10] == 0x04
                        && b[11] == 0x00 && b[12] == 0x00 && b[13] == 0x00) {
                    return "cin";
                }
                if (b[0] == 0x43 && b[1] == 0x61 && b[2] == 0x6C && b[3] == 0x69 && b[4] == 0x67 && b[5] == 0x61
                        && b[6] == 0x72 && b[7] == 0x69) {
                    return "cob";
                }
                if (b[0] == 0x43 && b[1] == 0x50 && b[2] == 0x54 && b[3] == 0x46 && b[4] == 0x49 && b[5] == 0x4C
                        && b[6] == 0x45) {
                    return "cpt";
                }
                if (b[0] == 0x43 && b[1] == 0x41 && b[2] == 0x4C && b[3] == 0x41 && b[4] == 0x4D && b[5] == 0x55
                        && b[6] == 0x53 && b[7] == 0x43 && b[8] == 0x56 && b[9] == 0x47) {
                    return "cvg";
                }
                if (b[0] == 0x56 && b[1] == 0x69 && b[2] == 0x73 && b[3] == 0x74 && b[4] == 0x61 && b[5] == 0x20
                        && b[6] == 0x44 && b[7] == 0x45 && b[8] == 0x4D && b[9] == 0x20 && b[10] == 0x46
                        && b[11] == 0x69 && b[12] == 0x6C && b[13] == 0x65) {
                    return "dem";
                }
                if (b[0] == 0x42 && b[1] == 0x4D && b[2] == 0x36) {
                    return "dib";
                }
                if (b[0] == 0x53 && b[1] == 0x44 && b[2] == 0x50 && b[3] == 0x58) {
                    return "dpx";
                }
                if (b[0] == 0x01 && b[1] == 0xFF && b[2] == 0x02 && b[3] == 0x04 && b[4] == 0x03 && b[5] == 0x02) {
                    return "drw";
                }
                if (b[0] == 0x41 && b[1] == 0x43 && b[2] == 0x31 && b[3] == 0x30) {
                    return "dwg";
                }
                if (b[0] == 0x65 && b[1] == 0x02 && b[2] == 0x01 && b[3] == 0x02) {
                    return "ecw";
                }
                if (b[0] == 0x01 && b[1] == 0x00 && b[2] == 0x00 && b[3] == 0x00 && b[4] == 0x58 && b[5] == 0x00
                        && b[6] == 0x00 && b[7] == 0x00) {
                    return "emf";
                }
                if (b[0] == 0xD0 && b[1] == 0xCF && b[2] == 0x11 && b[3] == 0xE0 && b[4] == 0xA1 && b[5] == 0xB1
                        && b[6] == 0x1A && b[7] == 0xE1 && b[8] == 0x00) {
                    return "fpx";
                }
                if (b[0] == 0x53 && b[1] == 0x49 && b[2] == 0x4D && b[3] == 0x50 && b[4] == 0x4C && b[5] == 0x45
                        && b[6] == 0x20 && b[7] == 0x20 && b[8] == 0x3D) {
                    return "fts";
                }
                if (b[0] == 0x48 && b[1] == 0x50 && b[2] == 0x48 && b[3] == 0x50 && b[4] == 0x34 && b[5] == 0x38
                        && b[6] == 0x2D && b[7] == 0x45 && b[8] == 0x1E && b[9] == 0x2B) {
                    return "gro";
                }
                if (b[0] == 0x6E && b[1] == 0x63 && b[2] == 0x6F && b[3] == 0x6C && b[4] == 0x73) {
                    return "hdr";
                }
                if (b[0] == 0x35 && b[1] == 0x4B && b[2] == 0x50 && b[3] == 0x35 && b[4] == 0x31 && b[5] == 0x5D
                        && b[6] == 0x2A && b[7] == 0x67 && b[8] == 0x72 && b[9] == 0x72 && b[10] == 0x80
                        && b[11] == 0x83 && b[12] == 0x85 && b[13] == 0x63) {
                    return "hru";
                }
                if (b[0] == 0xEB && b[1] == 0x3C && b[2] == 0x90 && b[3] == 0x2A) {
                    return "img";
                }
                if (b[0] == 0x65 && b[1] == 0x6C && b[2] == 0x6D && b[3] == 0x6F) {
                    return "infini-d";
                }
                if (b[0] == 0x49 && b[1] == 0x57 && b[2] == 0x43 && b[3] == 0x01) {
                    return "iwc";
                }
                if (b[0] == 0x80 && b[1] == 0x3E && b[2] == 0x44 && b[3] == 0x53 && b[4] == 0x43 && b[5] == 0x49
                        && b[6] == 0x4D) {
                    return "j6i";
                }
                if (b[0] == 0x4A && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x39 && b[4] == 0x39 && b[5] == 0x61) {
                    return "jif";
                }
                if (b[0] == 0x00 && b[1] == 0x00 && b[2] == 0x00 && b[3] == 0x0C && b[4] == 0x6A && b[5] == 0x50
                        && b[6] == 0x20 && b[7] == 0x20 && b[8] == 0x0D && b[9] == 0x0A && b[10] == 0x87
                        && b[11] == 0x0A) {
                    return "jp2";
                }
                if (b[0] == 0x4D && b[1] == 0x4D && b[2] == 0x00 && b[3] == 0x2A) {
                    return "kdc";
                }
                if (b[0] == 0x36 && b[1] == 0x34 && b[2] == 0x4C && b[3] == 0x41 && b[4] == 0x4E && b[5] == 0x20
                        && b[6] == 0x49 && b[7] == 0x44 && b[8] == 0x42 && b[9] == 0x4C && b[10] == 0x4F
                        && b[11] == 0x43 && b[12] == 0x4B) {
                    return "l64";
                }
                if (b[0] == 0x46 && b[1] == 0x4F && b[2] == 0x52 && b[3] == 0x4D) {
                    return "lbm";
                }
                if (b[0] == 0x49 && b[1] == 0x49 && b[2] == 0x2A && b[3] == 0x00 && b[4] == 0x08 && b[5] == 0x00
                        && b[6] == 0x00 && b[7] == 0x00 && b[8] == 0x0E && b[9] == 0x00 && b[10] == 0x00
                        && b[11] == 0x01 && b[12] == 0x04 && b[13] == 0x00) {
                    return "ldf";
                }
                if (b[0] == 0x57 && b[1] == 0x56 && b[2] == 0x02 && b[3] == 0x00 && b[4] == 0x47 && b[5] == 0x45
                        && b[6] == 0x00 && b[7] == 0x0E) {
                    return "lwf";
                }
                if (b[0] == 0x37 && b[1] == 0x00 && b[2] == 0x00 && b[3] == 0x10 && b[4] == 0x42 && b[5] == 0x00
                        && b[6] == 0x00 && b[7] == 0x10 && b[8] == 0x00 && b[9] == 0x00 && b[10] == 0x00
                        && b[11] == 0x00 && b[12] == 0x39 && b[13] == 0x64) {
                    return "mbm";
                }
                if (b[0] == 0x4D && b[1] == 0x47 && b[2] == 0x4C) {
                    return "mgl";
                }
                if (b[0] == 0x7B && b[1] == 0x0A && b[2] == 0x20 && b[3] == 0x20 && b[4] == 0x43 && b[5] == 0x72
                        && b[6] == 0x65 && b[7] == 0x61 && b[8] == 0x74 && b[9] == 0x65 && b[10] == 0x64) {
                    return "mif";
                }
                if (b[0] == 0x8A && b[1] == 0x4D && b[2] == 0x4E && b[3] == 0x47 && b[4] == 0x0D && b[5] == 0x0A
                        && b[6] == 0x1A && b[7] == 0x0A) {
                    return "mng";
                }
                if (b[0] == 0x4D && b[1] == 0x50 && b[2] == 0x46) {
                    return "mpw";
                }
                if (b[0] == 0x44 && b[1] == 0x61 && b[2] == 0x6E && b[3] == 0x4D) {
                    return "msp";
                }
                if (b[0] == 0x43 && b[1] == 0x36 && b[2] == 0x34) {
                    return "n64";
                }
                if (b[0] == 0x6E && b[1] == 0x6E && b[2] == 0x0A && b[3] == 0x00 && b[4] == 0x5E && b[5] == 0x00) {
                    return "ncr";
                }
                if (b[0] == 0x6E && b[1] == 0x66 && b[2] == 0x66) {
                    return "nff";
                }
                if (b[0] == 0x4E && b[1] == 0x47 && b[2] == 0x47 && b[3] == 0x00 && b[4] == 0x01 && b[5] == 0x00) {
                    return "ngg";
                }
                if (b[0] == 0x4E && b[1] == 0x4C && b[2] == 0x4D && b[3] == 0x20 && b[4] == 0x01 && b[5] == 0x02
                        && b[6] == 0x00) {
                    return "nlm";
                }
                if (b[0] == 0x4E && b[1] == 0x4F && b[2] == 0x4C && b[3] == 0x00 && b[4] == 0x01 && b[5] == 0x00
                        && b[6] == 0x06 && b[7] == 0x01 && b[8] == 0x03 && b[9] == 0x00) {
                    return "nol";
                }
                if (b[0] == 0x41 && b[1] == 0x48) {
                    return "pal";
                }
                if (b[0] == 0x50 && b[1] == 0x41 && b[2] == 0x58) {
                    return "pax";
                }
                if (b[0] == 0x63 && b[1] == 0x52 && b[2] == 0x01 && b[3] == 0x01 && b[4] == 0x38 && b[5] == 0x09
                        && b[6] == 0x3D && b[7] == 0x00) {
                    return "pcd";
                }
                if (b[0] == 0x1B && b[1] == 0x45 && b[2] == 0x1B && b[3] == 0x26 && b[4] == 0x6C && b[5] == 0x30
                        && b[6] == 0x4F && b[7] == 0x1B && b[8] == 0x26 && b[9] == 0x6C && b[10] == 0x30
                        && b[11] == 0x45 && b[12] == 0x1B && b[13] == 0x26) {
                    return "pcl";
                }
                if (b[0] == 0x50 && b[1] == 0x49 && b[2] == 0x58 && b[3] == 0x20) {
                    return "pix";
                }
                if (b[0] == 0x50 && b[1] == 0x4F && b[2] == 0x4C && b[3] == 0x20 && b[4] == 0x46 && b[5] == 0x6F
                        && b[6] == 0x72 && b[7] == 0x6D && b[8] == 0x61 && b[9] == 0x74) {
                    return "pol";
                }
                // Paint Shop Pro
                if (b[0] == 0x7E && b[1] == 0x42 && b[2] == 0x4B && b[3] == 0x00) {
                    return "psp";
                }
                if (b[0] == 0x50 && b[1] == 0x61 && b[2] == 0x69 && b[3] == 0x6E && b[4] == 0x74 && b[5] == 0x20
                        && b[6] == 0x53 && b[7] == 0x68 && b[8] == 0x6F && b[9] == 0x70 && b[10] == 0x20
                        && b[11] == 0x50 && b[12] == 0x72 && b[13] == 0x6F && b[14] == 0x20 && b[15] == 0x49
                        && b[16] == 0x6D && b[17] == 0x61 && b[18] == 0x67 && b[19] == 0x65 && b[20] == 0x20
                        && b[21] == 0x46 && b[22] == 0x69 && b[23] == 0x6C && b[24] == 0x65) {
                    return "psp";
                }
                if (b[0] == 0x51 && b[1] == 0x4C && b[2] == 0x49 && b[3] == 0x49 && b[4] == 0x46 && b[5] == 0x41
                        && b[6] == 0x58) {
                    return "qfx";
                }
                if (b[0] == 0x6D && b[1] == 0x6F && b[2] == 0x6F && b[3] == 0x76) {
                    return "qtm";
                }
                if (b[0] == 0x46 && b[1] == 0x4F && b[2] == 0x52 && b[3] == 0x4D && b[4] == 0x41 && b[5] == 0x54
                        && b[6] == 0x3D) {
                    return "rad";
                }
                if (b[0] == 0x59 && b[1] == 0xA6 && b[2] == 0x6A && b[3] == 0x95) {
                    return "ras";
                }
                if (b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x58 && b[3] == 0x33) {
                    return "rix";
                }
                if (b[0] == 0x23 && b[1] == 0x20 && b[2] == 0x24 && b[3] == 0x49 && b[4] == 0x64 && b[5] == 0x3A
                        && b[6] == 0x20) {
                    return "sid";
                }
                if (b[0] == 0x41 && b[1] == 0x75 && b[2] == 0x74 && b[3] == 0x6F && b[4] == 0x43 && b[5] == 0x41
                        && b[6] == 0x44 && b[7] == 0x20 && b[8] == 0x53 && b[9] == 0x6C && b[10] == 0x69
                        && b[11] == 0x64 && b[12] == 0x65) {
                    return "sld";
                }
                if (b[0] == 0x53 && b[1] == 0x74 && b[2] == 0x6F && b[3] == 0x72 && b[4] == 0x6D && b[5] == 0x33
                        && b[6] == 0x44) {
                    return "sod";
                }
                if (b[0] == 0xFA && b[1] == 0xDE && b[2] == 0xBA && b[3] == 0xBE && b[4] == 0x01 && b[5] == 0x01) {
                    return "wic";
                }
                if (b[0] == 0xD3 && b[1] == 0x23 && b[2] == 0x00 && b[3] == 0x00 && b[4] == 0x03 && b[5] == 0x00
                        && b[6] == 0x00 && b[7] == 0x00) {
                    return "wlm";
                }
                if (b[0] == 0xD7 && b[1] == 0xCD && b[2] == 0xC6 && b[3] == 0x9A) {
                    return "wmf";
                }
                if (b[0] == 0xFF && b[1] == 0x57 && b[2] == 0x50 && b[3] == 0x43 && b[4] == 0x10) {
                    return "wpg";
                }
                if (b[0] == 0x23 && b[1] == 0x56 && b[2] == 0x52 && b[3] == 0x4D && b[4] == 0x4C && b[5] == 0x20
                        && b[6] == 0x56 && b[7] == 0x32 && b[8] == 0x2E && b[9] == 0x30) {
                    return "wrl";
                }
                if (b[0] == 0x23 && b[1] == 0x64 && b[2] == 0x65 && b[3] == 0x66 && b[4] == 0x69 && b[5] == 0x6E
                        && b[6] == 0x65) {
                    return "xbm";
                }
                if (b[0] == 0x2F && b[1] == 0x2A && b[2] == 0x20 && b[3] == 0x58 && b[4] == 0x50 && b[5] == 0x4D
                        && b[6] == 0x20 && b[7] == 0x2A && b[8] == 0x2F) {
                    return "xpm";
                }
            } finally {
                is.reset();
            }
        }
        return null;
    }

    private TYPE getBufferDataType(final Buffer buffer) {
        TYPE bufferDataType = null;
        for (final TYPE type : TYPE.values()) {
            if (type.bufferClass.isAssignableFrom(buffer.getClass())) {
                bufferDataType = type;
                break;
            }
        }
        return bufferDataType;
    }

    protected void copyImageData(final Buffer src, final ByteBuffer dest, final TYPE bufferDataType) {
        final int srcPos = src.position();
        final int destPos = dest.position();
        switch (bufferDataType) {
            case BYTE:
                dest.put((ByteBuffer) src);
                break;
            case SHORT:
                dest.asShortBuffer().put((ShortBuffer) src);
                break;
            case CHAR:
                dest.asCharBuffer().put((CharBuffer) src);
                break;
            case INT:
                dest.asIntBuffer().put((IntBuffer) src);
                break;
            case FLOAT:
                dest.asFloatBuffer().put((FloatBuffer) src);
            case LONG:
                dest.asLongBuffer().put((LongBuffer) src);
                break;
            case DOUBLE:
                dest.asDoubleBuffer().put((DoubleBuffer) src);
                break;
            default:
                // it should never happen
        }
        src.position(srcPos);
        dest.position(destPos);
    }

    protected void flipImageData(final Buffer src, final ByteBuffer dest, final int dataSizeInBytes,
            final TYPE bufferDataType, final int width, final int height) {
        final int srcPos = src.position();
        final int destPos = dest.position();
        final int bytesPerPixel = dataSizeInBytes / (width * height);
        final int bytesPerElement = Buffers.sizeOfBufferElem(src);
        final int elementsPerPixel = bytesPerPixel / bytesPerElement;
        final int elementsPerLine = width * elementsPerPixel;
        final int bytesPerLine = bytesPerPixel * width;// width = pixels per line
        byte[] byteBuf = null;
        short[] shortBuf = null;
        char[] charBuf = null;
        int[] intBuf = null;
        float[] floatBuf = null;
        long[] longBuf = null;
        double[] doubleBuf = null;
        switch (bufferDataType) {
            case BYTE:
                byteBuf = new byte[elementsPerLine];
                break;
            case SHORT:
                shortBuf = new short[elementsPerLine];
                break;
            case CHAR:
                charBuf = new char[elementsPerLine];
                break;
            case INT:
                intBuf = new int[elementsPerLine];
                break;
            case FLOAT:
                floatBuf = new float[elementsPerLine];
                break;
            case LONG:
                longBuf = new long[elementsPerLine];
                break;
            case DOUBLE:
                doubleBuf = new double[elementsPerLine];
                break;
            default:
                // it should never happen
        }
        while (dest.hasRemaining()) {
            final int srcFirstPixelIndex = dest.position() / bytesPerPixel;
            final int srcFirstPixelComponentOffset = dest.position() - (srcFirstPixelIndex * bytesPerPixel);
            final int srcFirstColumnIndex = srcFirstPixelIndex % width;
            final int scrFirstRowIndex = (srcFirstPixelIndex - srcFirstColumnIndex) / width;
            final int dstFirstColumnIndex = srcFirstColumnIndex;
            final int dstFirstRowIndex = (height - 1) - scrFirstRowIndex;
            final int dstFirstPixelIndex = dstFirstRowIndex * width + dstFirstColumnIndex;
            final int dstFirstPixelComponentOffset = srcFirstPixelComponentOffset;
            final int dstFirstElementIndex = dstFirstPixelIndex * bytesPerPixel + dstFirstPixelComponentOffset;
            switch (bufferDataType) {
                case BYTE:
                    ((ByteBuffer) src).position(dstFirstElementIndex);
                    ((ByteBuffer) src).get(byteBuf);
                    dest.put(byteBuf);
                    break;
                case SHORT:
                    ((ShortBuffer) src).position(dstFirstElementIndex);
                    ((ShortBuffer) src).get(shortBuf);
                    dest.asShortBuffer().put(shortBuf);
                    dest.position(dest.position() + bytesPerLine);
                    break;
                case CHAR:
                    ((CharBuffer) src).position(dstFirstElementIndex);
                    ((CharBuffer) src).get(charBuf);
                    dest.asCharBuffer().put(charBuf);
                    dest.position(dest.position() + bytesPerLine);
                    break;
                case INT:
                    ((IntBuffer) src).position(dstFirstElementIndex);
                    ((IntBuffer) src).get(intBuf);
                    dest.asIntBuffer().put(intBuf);
                    dest.position(dest.position() + bytesPerLine);
                    break;
                case FLOAT:
                    ((FloatBuffer) src).position(dstFirstElementIndex);
                    ((FloatBuffer) src).get(floatBuf);
                    dest.asFloatBuffer().put(floatBuf);
                    dest.position(dest.position() + bytesPerLine);
                    break;
                case LONG:
                    ((LongBuffer) src).position(dstFirstElementIndex);
                    ((LongBuffer) src).get(longBuf);
                    dest.asLongBuffer().put(longBuf);
                    dest.position(dest.position() + bytesPerLine);
                    break;
                case DOUBLE:
                    ((DoubleBuffer) src).position(dstFirstElementIndex);
                    ((DoubleBuffer) src).get(doubleBuf);
                    dest.asDoubleBuffer().put(doubleBuf);
                    dest.position(dest.position() + bytesPerLine);
                    break;
                default:
                    // it should never happen
            }
        }
        src.position(srcPos);
        dest.position(destPos);
    }

    public boolean isMipmapsProductionEnabled() {
        return _mipmapsProductionEnabled;
    }

    public void setMipmapsProductionEnabled(final boolean mipmapsProductionEnabled) {
        _mipmapsProductionEnabled = mipmapsProductionEnabled;
    }
}
