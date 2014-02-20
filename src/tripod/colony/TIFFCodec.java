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
                    
                case TAG_XRESOLUTION:
                    params.put(TAG_XRESOLUTION, f.getAsFloat(0));
                    break;
                    
                case TAG_YRESOLUTION:
                    params.put(TAG_YRESOLUTION, f.getAsFloat(0));
                    break;
                
                case TAG_ROWSPERSTRIP:
                    params.put(TAG_ROWSPERSTRIP, f.getAsFloat(0));
                    break;
                    
                case TAG_PHOTOMETRIC:
                    params.put(TAG_PHOTOMETRIC, f.getAsInt(0));
                    break;
                    
                case TAG_BITSPERSAMPLE:
                    params.put(TAG_BITSPERSAMPLE, f.getAsInt(0));
                    break;
                    
                case TAG_IMAGEWIDTH: 
                    params.put(TAG_IMAGEWIDTH, f.getAsFloat(0));
                    break;
                    
                case TAG_IMAGELENGTH:	
                    params.put(TAG_IMAGELENGTH, f.getAsFloat(0));
                    break;

                case TAG_DATETIME:
                    params.put(TAG_DATETIME, f.getAsString(0));
                    break;

                case TAG_DOCUMENTNAME:
                    params.put(TAG_DOCUMENTNAME, f.getAsString(0));
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

	logger.info(file + " has " + ndirs + " image; "+params);
        return decoder.decodeAsRenderedImage();
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
}
