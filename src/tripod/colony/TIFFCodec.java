package tripod.colony;

import java.io.*;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;;
import java.awt.image.WritableRaster;
import com.sun.media.jai.codec.*;

public class TIFFCodec implements TIFFTags {
    static final Logger logger = Logger.getLogger(TIFFCodec.class.getName());

    public static RenderedImage decode (File file) throws IOException {
        return decode (file, new HashMap ());
    }

    public static RenderedImage decode (File file, Map params) 
        throws IOException {
        ImageDecoder decoder = ImageCodec.createImageDecoder
	    ("TIFF", file, new TIFFDecodeParam ());

	int ndirs = decoder.getNumPages();
	TIFFDirectory tif = new TIFFDirectory (decoder.getInputStream(), 0);
	TIFFField[] fields = tif.getFields();

	for (int j = 0; j < fields.length; ++j) {
	    TIFFField f = fields[j];
	    int tag = f.getTag();
            try {
                switch (tag) {
                case TAG_RESOLUTIONUNIT:
                    {
                        int u = f.getAsInt(0);
                        if (u == RESOLUTIONUNIT_NONE) {
                        }
                        else if (u == RESOLUTIONUNIT_INCH) {
                            params.put(TAG_RESOLUTIONUNIT, "in");
                        }
                        else if (u == RESOLUTIONUNIT_CENT) {
                            params.put(TAG_RESOLUTIONUNIT, "cm");
                        }
                    }
                    break;
                    
                case TAG_PHOTOMETRIC:
                case TAG_BITSPERSAMPLE:
                case TAG_SAMPLESPERPIXEL:
                    params.put(tag, f.getAsInt(0));
                    break;
                    
                case TAG_IMAGEWIDTH:
                case TAG_IMAGELENGTH:
                case TAG_XRESOLUTION:
                case TAG_YRESOLUTION:
                case TAG_ROWSPERSTRIP:
                    params.put(tag, f.getAsFloat(0));
                    break;

                case TAG_DATETIME:
                case TAG_DOCUMENTNAME:
                case TAG_SOFTWARE:
                case TAG_IMAGEDESCRIPTION:
                    params.put(tag, f.getAsString(0));
                    break;
                }
            }
            catch (Exception ex) {
                logger.warning("## TIFF decoder tag="
                               +tag+"; "+ex.getMessage());
            }
	}

	/*
          if (xres > 0) {
          width /= xres;
          }
          if (yres > 0) {
          height /= yres;
          }
	*/

        Integer photo = (Integer)params.get(TAG_PHOTOMETRIC);
        RenderedImage image = decoder.decodeAsRenderedImage();
        if (photo != null && photo == PHOTOMETRIC_WHITEISZERO) {
            int bps = (Integer)params.get(TAG_BITSPERSAMPLE);
        }

	logger.info(file + " has " + ndirs + " image; "+params);
        return image;
    }

    public static void encode (String file, RenderedImage image) 
        throws IOException {
        encode (new File (file), image);
    }

    public static void encode (File file, RenderedImage image) 
        throws IOException {
        encode (new FileOutputStream (file), image);
    }

    public static void encode (OutputStream os, RenderedImage image) 
        throws IOException {
        if (image == null) {
            throw new IllegalArgumentException ("Image is null");
        }

        TIFFEncodeParam param = new TIFFEncodeParam ();
        //param.setCompression (TIFFEncodeParam.COMPRESSION_GROUP4);
        ImageEncoder encoder = ImageCodec.createImageEncoder
            ("TIFF", os, param);
        encoder.encode(image);
    }

    public static void encode (String file, Raster raster, 
                               ColorModel model) throws IOException {
        encode (new FileOutputStream (file), raster, model);
    }

    public static void encode (File file, Raster raster, 
                               ColorModel model) throws IOException {
        encode (new FileOutputStream (file), raster, model);
    }

    public static void encode (OutputStream os, Raster raster, 
                               ColorModel model) throws IOException {
        if (raster == null) {
            throw new IllegalArgumentException ("Raster is null");
        }

        TIFFEncodeParam param = new TIFFEncodeParam ();
        ImageEncoder encoder = ImageCodec.createImageEncoder
            ("TIFF", os, param);
        encoder.encode(raster, model);        
    }

    public static void main (String[] argv) throws Exception {
        for (String a : argv) {
            Map params = new HashMap ();
            RenderedImage image = decode (new File (a), params);
            javax.imageio.ImageIO.write(Util.rescale(image.getData(), 5000),
                                        "png", new File ("image.png"));

        }
    }
}
