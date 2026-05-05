package filters;

public interface Filter {
    double filter(double input);
    byte[] filter(byte[] input);
    void reset();
}