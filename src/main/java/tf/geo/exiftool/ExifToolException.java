package tf.geo.exiftool;

/**
 * ExifTool could not be started, crashed or didn't answer in time.
 * Errors that ExifTool reports for single files are not exceptions - they are part of the ExecResult.
 */
public class ExifToolException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ExifToolException(String message) {
        super(message);
    }

    public ExifToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
