package denominator.model;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Objects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.beans.ConstructorProperties;
import java.util.Map;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ForwardingList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.UnsignedInteger;

/**
 * A grouping of resource records by name and type. In implementation, this is
 * an immutable list of rdata values corresponding to records sharing the same
 * {@link #getName() name} and {@link #getType}.
 * 
 * @param <D>
 *            RData type shared across elements. This may be empty in the case
 *            of special configuration such as `alias`.
 * 
 * @see <a href="http://www.ietf.org/rfc/rfc1035.txt">RFC 1035</a>
 */
public class ResourceRecordSet<D extends Map<String, Object>> extends ForwardingList<D> {

    private final String name;
    private final String type;
    private final Optional<Integer> ttl;
    private final ImmutableList<D> rdata;
    private final ImmutableMap<String, Map<String, Object>> config;

    @ConstructorProperties({ "name", "type", "ttl", "rdata", "config" })
    ResourceRecordSet(String name, String type, Optional<Integer> ttl, ImmutableList<D> rdata,
            ImmutableMap<String, Map<String, Object>> config) {
        this.name = checkNotNull(name, "name");
        checkArgument(name.length() <= 255, "Name must be limited to 255 characters"); 
        this.type = checkNotNull(type, "type of %s", name);
        this.ttl = ttl != null ? ttl : Optional.<Integer> absent(); //temporary until jcloud 1.6.0-rc.2
        checkArgument(UnsignedInteger.fromIntBits(this.ttl.or(0)).longValue() <= 0x7FFFFFFFL, // Per RFC 2181 
                "Invalid ttl value: %s, must be 0-2147483647", this.ttl);
        this.rdata = checkNotNull(rdata, "rdata of %s", name);
        this.config = checkNotNull(config, "config of %s", name);
    }

    /**
     * an owner name, i.e., the name of the node to which this resource record
     * pertains.
     */
    public String getName() {
        return name;
    }

    /**
     * The mnemonic type of the record. ex {@code CNAME}
     */
    public String getType() {
        return type;
    }

    /**
     * the time interval that the resource record may be cached. Zero implies it
     * is not cached. Absent means default for the zone.
     */
    public Optional<Integer> getTTL() {
        return ttl;
    }

    /**
     * server-side configuration of the record set, often used to decide if this
     * record set is visible to a client or not. If empty, this is a normal
     * record, visible to all resolvers.
     * 
     * For example, if this record set is intended for resolvers in Utah, config
     * will likely contain an entry with a key of "geo" and value
     * {@link denominator.model.config.Geo}, where
     * {@link denominator.model.config.Geo#getTerritories()} contains something
     * like `Utah` or `US-UT`.
     */
    public Map<String, Map<String, Object>> getConfig() {
        return config;
    }

    @Override
    protected ImmutableList<D> delegate() {
        return rdata;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, type, rdata, config);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || !(obj instanceof ResourceRecordSet))
            return false;
        ResourceRecordSet<?> that = ResourceRecordSet.class.cast(obj);
        return equal(this.name, that.name) && equal(this.type, that.type) && equal(this.rdata, that.rdata)
                && equal(this.config, that.config);
    }

    @Override
    public String toString() {
        return toStringHelper(this).omitNullValues()
                                   .add("name", name)
                                   .add("type", type)
                                   .add("ttl", ttl.orNull())
                                   .add("rdata", rdata.isEmpty() ? null : rdata)
                                   .add("config", config.isEmpty() ? null : config).toString();
    }

    public static <D extends Map<String, Object>> Builder<D> builder() {
        return new Builder<D>();
    }

    /**
     * Allows creation or mutation of record sets based on the portable RData
     * form {@code D} as extends {@code Map<String, Object>}
     * 
     * @param <D>
     *            RData type shared across elements. see
     *            {@link denominator.model.rdata}
     * 
     */
    public static class Builder<D extends Map<String, Object>> extends AbstractRecordSetBuilder<D, D, Builder<D>> {

        private ImmutableList.Builder<D> rdata = ImmutableList.builder();

        /**
         * adds a value to the builder.
         * 
         * ex.
         * 
         * <pre>
         * builder.add(srvData);
         * </pre>
         */
        public Builder<D> add(D rdata) {
            this.rdata.add(checkNotNull(rdata, "rdata"));
            return this;
        }

        /**
         * replaces all rdata values in the builder
         * 
         * ex.
         * 
         * <pre>
         * builder.addAll(srvData1, srvData2);
         * </pre>
         */
        public Builder<D> addAll(D... rdata) {
            this.rdata.addAll(ImmutableList.copyOf(checkNotNull(rdata, "rdata")));
            return this;
        }

        /**
         * replaces all rdata values in the builder
         * 
         * ex.
         * 
         * <pre>
         * 
         * builder.addAll(otherRecordSet);
         * </pre>
         */
        public <R extends D> Builder<D> addAll(Iterable<R> rdata) {
            this.rdata.addAll(checkNotNull(rdata, "rdata"));
            return this;
        }

        @Override
        protected ImmutableList<D> rdataValues() {
            return rdata.build();
        }
    }
}
