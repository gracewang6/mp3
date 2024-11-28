package fsft.wikipedia;

public class WikiMediatorRequest {
    private final long time;
    private final String string;

    public WikiMediatorRequest(String str) {
        time = System.currentTimeMillis();
        string = str;
    }

    public WikiMediatorRequest() {
        this("");
    }

    public long getTime() {
        return time;
    }

    public String getString() {
        return string;
    }
}
