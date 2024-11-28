package fsft.wikipedia;

import fsft.fsftbuffer.Bufferable;
import io.github.fastily.jwiki.core.Wiki;

public class WikiPage implements Bufferable {
    private String pageTitle;
    private String text;

    public WikiPage(Wiki wiki, String pageTitle) {
        this.pageTitle = pageTitle;
        text = wiki.getPageText(pageTitle);
    }

    public String getText() {
        return text;
    }

    @Override
    public String id() {
        return pageTitle;
    }
}
