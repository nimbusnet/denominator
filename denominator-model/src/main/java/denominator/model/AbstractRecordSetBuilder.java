package denominator.model;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Capable of building record sets from rdata input types expressed as {@code E}
 * 
 * @param <E>
 *            input type of rdata
 * @param <D>
 *            portable type of the rdata in the {@link ResourceRecordSet}
 */
abstract class AbstractRecordSetBuilder<E, D extends Map<String, Object>, B extends AbstractRecordSetBuilder<E, D, B>> {

    private String name;
    private String type;
    private Optional<Integer> ttl = Optional.absent();
    private ImmutableMap.Builder<String, Map<String, Object>> config = ImmutableMap.builder();

    /**
     * @see ResourceRecordSet#getName()
     */
    @SuppressWarnings("unchecked")
    public B name(String name) {
        this.name = name;
        return (B) this;
    }

    /**
     * @see ResourceRecordSet#getType()
     */
    @SuppressWarnings("unchecked")
    public B type(String type) {
        this.type = type;
        return (B) this;
    }

    /**
     * @see ResourceRecordSet#getTTL()
     */
    @SuppressWarnings("unchecked")
    public B ttl(Integer ttl) {
        this.ttl = Optional.fromNullable(ttl);
        return (B) this;
    }

    /**
     * adds a value to the builder.
     * 
     * ex.
     * 
     * <pre>
     * builder.putConfig("geo", geo);
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public B putConfig(String key, Map<String, Object> config) {
        this.config.put(checkNotNull(key, "key"), checkNotNull(config, "config"));
        return (B) this;
    }

    /**
     * adds config values in the builder
     * 
     * ex.
     * 
     * <pre>
     * 
     * builder.putAllConfig(otherConfig);
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public B putAllConfig(Map<String, Map<String, Object>> config) {
        this.config.putAll(checkNotNull(config, "config"));
        return (B) this;
    }

    /**
     * @see ResourceRecordSetWithConfig#getConfig()
     */
    @SuppressWarnings("unchecked")
    public B config(Map<String, Map<String, Object>> config) {
        this.config = ImmutableMap.<String, Map<String, Object>> builder().putAll(config);
        return (B) this;
    }

    public ResourceRecordSet<D> build() {
        return new ResourceRecordSet<D>(name, type, ttl, rdataValues(), config.build());
    }

    /**
     * aggregate collected rdata values
     */
    protected abstract ImmutableList<D> rdataValues();
}
