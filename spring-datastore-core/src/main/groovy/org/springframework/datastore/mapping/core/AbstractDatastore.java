/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.datastore.mapping.core;

import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.datastore.mapping.engine.EntityInterceptor;
import org.springframework.datastore.mapping.engine.EntityInterceptorAware;
import org.springframework.datastore.mapping.keyvalue.mapping.KeyValue;
import org.springframework.datastore.mapping.model.MappingContext;
import org.springframework.datastore.mapping.model.PersistentProperty;
import org.springframework.datastore.mapping.model.PropertyMapping;
import org.springframework.datastore.mapping.model.types.BasicTypeConverterRegistrar;
import org.springframework.datastore.mapping.validation.ValidatingInterceptor;

import java.util.*;

/**
 * Abstract Datastore implementation that deals with binding the Session to thread locale upon creation.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractDatastore implements Datastore, EntityInterceptorAware {

    private static ThreadLocal<Session> currentConnectionThreadLocal = new InheritableThreadLocal<Session>();

    protected MappingContext mappingContext;
    protected Map<String, String> connectionDetails = Collections.emptyMap();
    protected List<EntityInterceptor> interceptors = new ArrayList<EntityInterceptor>();

    public AbstractDatastore(MappingContext mappingContext) {
        this.mappingContext = mappingContext;
    }

    public AbstractDatastore(MappingContext mappingContext, Map<String, String> connectionDetails) {
        this.mappingContext = mappingContext;
        this.connectionDetails = connectionDetails;
        addEntityInterceptor(new ValidatingInterceptor());
    }

    public void setConnectionDetails(Map<String, String> connectionDetails) {
        this.connectionDetails = connectionDetails;
    }

    public Session connect() {
        return connect(this.connectionDetails);
    }

    public void addEntityInterceptor(EntityInterceptor interceptor) {
        if(interceptor != null) {
            interceptor.setDatastore(this);
            this.interceptors.add(interceptor);
        }
    }

    public void setEntityInterceptors(List<EntityInterceptor> interceptors) {
        if(interceptors!=null) this.interceptors = interceptors;
    }

    public AbstractDatastore() {
    }

    public final Session connect(Map<String, String> connectionDetails) {
        final Session session = createSession(connectionDetails);

        if(session != null) {
            session.setEntityInterceptors(this.interceptors);
            currentConnectionThreadLocal.set(session);
        }
        return session;
    }

    /**
     * Creates the native session
     *
     * @param connectionDetails The session details
     * @return The session object
     */
    protected abstract Session createSession(Map<String, String> connectionDetails);

    public final Session getCurrentSession() throws ConnectionNotFoundException {
        final Session connection = currentConnectionThreadLocal.get();
        if(connection == null) {
            throw new ConnectionNotFoundException("Not datastore session found. Call Datastore.connect(..) before calling Datastore.getCurrentSession()");
        }
        return connection;
    }

    /**
     * Static way to retrieve the session
     * @return The session instance
     * @throws ConnectionNotFoundException If no session has been created
     */
    public static Session retrieveSession() throws ConnectionNotFoundException {
        final Session connection = currentConnectionThreadLocal.get();
        if(connection == null) {
            throw new ConnectionNotFoundException("Not datastore session found. Call Datastore.connect(..) before calling Datastore.getCurrentSession()");
        }
        return connection;
    }

    /**
     * Binds the session to the current thread local
     * @param session The session
     */
    public static void bindSession(Session session) {
        if(session != null) {
            currentConnectionThreadLocal.set(session);
        }
    }

    public MappingContext getMappingContext() {
        return mappingContext;
    }

    /**
     * Clears the thread bound session, should be called by the
     * {@link Session#disconnect()}
     */
    public static void clearCurrentConnection() {
        currentConnectionThreadLocal.set(null);
    }

    protected void initializeConverters(MappingContext mappingContext) {
        final ConverterRegistry conversionService = mappingContext.getConverterRegistry();
        BasicTypeConverterRegistrar registrar = new BasicTypeConverterRegistrar();
        registrar.register(conversionService);
    }

    protected boolean isIndexed(PersistentProperty property) {
        PropertyMapping<KeyValue> pm = property.getMapping();
        final KeyValue keyValue = pm.getMappedForm();
        return keyValue != null && keyValue.isIndex();
    }
}
