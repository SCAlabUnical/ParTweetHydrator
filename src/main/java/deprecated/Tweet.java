package deprecated;


public class Tweet {
    private long id;
    private String created_at;
    private String id_str;
    private String text;
    private String source;
    private boolean truncated;
    private long in_reply_to_status_id;
    private String in_reply_to_status_id_str;
    private String in_reply_to_screen_name;
    private long quoted_status_id;
    private String quoted_status_id_str;
    private boolean is_quote_status;
    private Tweet quoted_status;
    private Tweet retweeted_status;
    private int quote_count;
    private int reply_count;
    private int retweet_count;
    private int favorite_count;
    private boolean favorited;
    private boolean retweeted;
    private boolean possibly_sensitive;
    private String filter_level;
    private String lang;
    private String[] withheld_in_countries;

    public long getQuoted_status_id() {
        return quoted_status_id;
    }

    public void setQuoted_status_id(long quoted_status_id) {
        this.quoted_status_id = quoted_status_id;
    }

    public String getQuoted_status_id_str() {
        return quoted_status_id_str;
    }

    public void setQuoted_status_id_str(String quoted_status_id_str) {
        this.quoted_status_id_str = quoted_status_id_str;
    }

    public boolean isIs_quote_status() {
        return is_quote_status;
    }

    public void setIs_quote_status(boolean is_quote_status) {
        this.is_quote_status = is_quote_status;
    }

    public Tweet getQuoted_status() {
        return quoted_status;
    }

    public void setQuoted_status(Tweet quoted_status) {
        this.quoted_status = quoted_status;
    }

    public Tweet getRetweeted_status() {
        return retweeted_status;
    }

    public void setRetweeted_status(Tweet retweeted_status) {
        this.retweeted_status = retweeted_status;
    }

    public int getQuote_count() {
        return quote_count;
    }

    public void setQuote_count(int quote_count) {
        this.quote_count = quote_count;
    }

    public int getReply_count() {
        return reply_count;
    }

    public void setReply_count(int reply_count) {
        this.reply_count = reply_count;
    }

    public int getRetweet_count() {
        return retweet_count;
    }

    public void setRetweet_count(int retweet_count) {
        this.retweet_count = retweet_count;
    }

    public int getFavorite_count() {
        return favorite_count;
    }

    public void setFavorite_count(int favorite_count) {
        this.favorite_count = favorite_count;
    }

    public boolean isFavorited() {
        return favorited;
    }

    public void setFavorited(boolean favorited) {
        this.favorited = favorited;
    }

    public boolean isRetweeted() {
        return retweeted;
    }

    public void setRetweeted(boolean retweeted) {
        this.retweeted = retweeted;
    }

    public boolean isPossibly_sensitive() {
        return possibly_sensitive;
    }

    public void setPossibly_sensitive(boolean possibly_sensitive) {
        this.possibly_sensitive = possibly_sensitive;
    }

    public String getFilter_level() {
        return filter_level;
    }

    public void setFilter_level(String filter_level) {
        this.filter_level = filter_level;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String[] getWithheld_in_countries() {
        return withheld_in_countries;
    }

    public void setWithheld_in_countries(String[] withheld_in_countries) {
        this.withheld_in_countries = withheld_in_countries;
    }

    public Tweet() {

    }

    @Override
    public String toString() {
        return "{" +
                "\"id\"=" + id +
                ",\"created_at\"='" + created_at + '\'' +
                ", id_str='" + id_str + '\'' +
                ", text='" + text + '\'' +
                ", source='" + source + '\'' +
                ", truncated=" + truncated +
                ", in_reply_to_status_id=" + in_reply_to_status_id +
                ", in_reply_to_status_id_str='" + in_reply_to_status_id_str + '\'' +
                ", in_reply_to_screen_name='" + in_reply_to_screen_name + '\'' +
                '}';
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getCreated_at() {
        return created_at;
    }

    public void setCreated_at(String created_at) {
        this.created_at = created_at;
    }

    public String getId_str() {
        return id_str;
    }

    public void setId_str(String id_str) {
        this.id_str = id_str;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public long getIn_reply_to_status_id() {
        return in_reply_to_status_id;
    }

    public void setIn_reply_to_status_id(long in_reply_to_status_id) {
        this.in_reply_to_status_id = in_reply_to_status_id;
    }

    public String getIn_reply_to_status_id_str() {
        return in_reply_to_status_id_str;
    }

    public void setIn_reply_to_status_id_str(String in_reply_to_status_id_str) {
        this.in_reply_to_status_id_str = in_reply_to_status_id_str;
    }

    public String getIn_reply_to_screen_name() {
        return in_reply_to_screen_name;
    }

    public void setIn_reply_to_screen_name(String in_reply_to_screen_name) {
        this.in_reply_to_screen_name = in_reply_to_screen_name;
    }
}

