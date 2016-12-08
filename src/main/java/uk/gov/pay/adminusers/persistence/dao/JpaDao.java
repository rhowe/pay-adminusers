package uk.gov.pay.adminusers.persistence.dao;

import com.google.inject.Provider;
import com.google.inject.persist.Transactional;

import javax.persistence.EntityManager;
import java.util.Optional;

@Transactional
public abstract class JpaDao<T> {

    protected final Provider<EntityManager> entityManager;
    private final Class<T> persistenceClass;

    protected JpaDao(Provider<EntityManager> entityManager, Class<T> persistenceClass) {
        this.entityManager = entityManager;
        this.persistenceClass = persistenceClass;
    }

    public void persist(final T object) {
        entityManager.get().persist(object);
    }

    public void remove(final T object) {
        entityManager.get().remove(object);
    }

    public <ID> Optional<T> findById(final ID id) {
        return Optional.ofNullable(entityManager.get().find(persistenceClass, id));
    }

    public T merge(final T object) {
        return entityManager.get().merge(object);
    }
}
