package no.nav.sbl.pdfutility;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.tika.Tika;

import java.util.function.Predicate;

public class FiletypeSjekker {

    public static final Predicate<byte[]> IS_PNG = bytes ->
            (new Tika()).detect(ArrayUtils.subarray(bytes.clone(), 0, 2048)).equalsIgnoreCase("image/png");
    public static final Predicate<byte[]> IS_PDF = bytes ->
            (new Tika()).detect(bytes).equalsIgnoreCase("application/pdf");
    public static final Predicate<byte[]> IS_JPG = bytes ->
            (new Tika()).detect(bytes).equalsIgnoreCase("image/jpeg");
    public static final Predicate<byte[]> IS_IMAGE = bytes -> IS_PNG.test(bytes) || IS_JPG.test(bytes);

    public static boolean isPng(byte[] bytes) {
        return IS_PNG.test(bytes);
    }

    public static boolean isPdf(byte[] bytes) {
        return IS_PDF.test(bytes);
    }

    public static boolean isJpg(byte[] bytes) {
        return IS_JPG.test(bytes);
    }

    public static boolean isImage(byte[] bytes) {
        return IS_IMAGE.test(bytes);
    }
}
