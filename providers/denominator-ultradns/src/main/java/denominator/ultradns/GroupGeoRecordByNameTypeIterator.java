package denominator.ultradns;

import static com.google.common.collect.Iterators.peekingIterator;
import static denominator.ultradns.UltraDNSFunctions.forTypeAndRData;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.jclouds.ultradns.ws.domain.DirectionalPool;
import org.jclouds.ultradns.ws.domain.DirectionalPoolRecord;
import org.jclouds.ultradns.ws.domain.DirectionalPoolRecordDetail;
import org.jclouds.ultradns.ws.domain.IdAndName;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.PeekingIterator;

import denominator.model.ResourceRecordSet;
import denominator.model.ResourceRecordSet.Builder;
import denominator.model.config.Geo;

/**
 * Generally, this iterator will produce {@link ResourceRecordSet} for
 * only a single record type. However, there are special cases where this can
 * produce multiple. For example, {@link DirectionalPool.RecordType#IPV4} and
 * {@link DirectionalPool.RecordType#IPV6} emit both address ({@code A} or
 * {@code AAAA}) and {@code CNAME} records.
 * 
 * @author adrianc
 * 
 */
class GroupGeoRecordByNameTypeIterator implements Iterator<ResourceRecordSet<?>> {

    static final class Factory {
        private final CacheLoader<String, List<String>> directionalGroupIdToTerritories;

        @Inject
        private Factory(CacheLoader<String, List<String>> directionalGroupIdToTerritories) {
            this.directionalGroupIdToTerritories = directionalGroupIdToTerritories;
        }

        /**
         * @param sortedIterator
         *            only contains records with the same
         *            {@link DirectionalPoolRecordDetail#getName()}, sorted by
         *            {@link DirectionalRecord#getType()},
         *            {@link DirectionalPoolRecordDetail#getGeolocationGroup()} or
         *            {@link DirectionalPoolRecordDetail#getGroup()}
         */
        Iterator<ResourceRecordSet<?>> create(Iterator<DirectionalPoolRecordDetail> sortedIterator) {
            LoadingCache<String, List<String>> requestScopedGeoCache = CacheBuilder.newBuilder().build(
                    directionalGroupIdToTerritories);
            return new GroupGeoRecordByNameTypeIterator(requestScopedGeoCache, sortedIterator);
        }
    }

    private final Function<String, List<String>> directionalGroupIdToTerritories;
    private final PeekingIterator<DirectionalPoolRecordDetail> peekingIterator;

    private GroupGeoRecordByNameTypeIterator(Function<String, List<String>> directionalGroupIdToTerritories,
            Iterator<DirectionalPoolRecordDetail> sortedIterator) {
        this.directionalGroupIdToTerritories = directionalGroupIdToTerritories;
        this.peekingIterator = peekingIterator(sortedIterator);
    }

    /**
     * skips no response records as they aren't portable
     */
    @Override
    public boolean hasNext() {
        if (!peekingIterator.hasNext())
            return false;
        DirectionalPoolRecordDetail record = peekingIterator.peek();
        if (record.getRecord().isNoResponseRecord()) {
            // TODO: log as this is unsupported
            peekingIterator.next();
        }
        return peekingIterator.hasNext();
    }

    static Optional<IdAndName> group(DirectionalPoolRecordDetail in) {
        return in.getGeolocationGroup().or(in.getGroup());
    }

    @Override
    public ResourceRecordSet<?> next() {
        DirectionalPoolRecordDetail directionalRecord = peekingIterator.next();
        DirectionalPoolRecord record = directionalRecord.getRecord();

        Builder<Map<String, Object>> builder = ResourceRecordSet.builder()
                                                                .name(directionalRecord.getName())
                                                                .type(record.getType())
                                                                .ttl(record.getTTL());

        builder.add(forTypeAndRData(record.getType(), record.getRData()));

        IdAndName directionalGroup = group(directionalRecord).get();
        Geo config =  Geo.create(directionalGroup.getName(), 
                                 directionalGroupIdToTerritories.apply(directionalGroup.getId()));
        
        while (hasNext()) {
            DirectionalPoolRecordDetail next = peekingIterator.peek();
            if (typeTTLAndGeoGroupEquals(next, directionalRecord)) {
                peekingIterator.next();
                builder.add(forTypeAndRData(record.getType(), next.getRecord().getRData()));
            } else {
                break;
            }
        }
        return builder.putConfig("geo", config).build();
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    static boolean typeTTLAndGeoGroupEquals(DirectionalPoolRecordDetail actual, DirectionalPoolRecordDetail expected) {
        return actual.getRecord().getType() == expected.getRecord().getType()
                && actual.getRecord().getTTL() == expected.getRecord().getTTL()
                && group(actual).equals(group(expected));
    }
}