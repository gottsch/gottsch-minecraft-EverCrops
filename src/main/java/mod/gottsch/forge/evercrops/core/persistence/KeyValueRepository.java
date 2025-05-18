package mod.gottsch.forge.evercrops.core.persistence;

/**
 * @author by Mark Gottschling on 5/16/2025
 */
@Deprecated
public interface KeyValueRepository<K, V> {
    void save(K key, V value);
    V find(K key);
    void delete(K key);
}
