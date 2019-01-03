/*
 * Copyright 2018 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.plugin.mongo.interceptor;

import com.mongodb.MongoClientOptions;
import com.mongodb.ServerAddress;
import com.mongodb.connection.Cluster;
import com.mongodb.connection.ClusterDescription;
import com.mongodb.connection.ServerDescription;
import com.navercorp.pinpoint.bootstrap.context.DatabaseInfo;
import com.navercorp.pinpoint.bootstrap.interceptor.AroundInterceptor;
import com.navercorp.pinpoint.bootstrap.logging.PLogger;
import com.navercorp.pinpoint.bootstrap.logging.PLoggerFactory;
import com.navercorp.pinpoint.bootstrap.plugin.jdbc.DatabaseInfoAccessor;
import com.navercorp.pinpoint.bootstrap.plugin.jdbc.MongoDatabaseInfo;
import com.navercorp.pinpoint.bootstrap.plugin.jdbc.UnKnownDatabaseInfo;
import com.navercorp.pinpoint.bootstrap.util.InterceptorUtils;
import com.navercorp.pinpoint.common.plugin.util.HostAndPort;
import com.navercorp.pinpoint.plugin.mongo.MongoConstants;
import com.navercorp.pinpoint.plugin.mongo.MongoUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Roy Kim
 */
public class MongoDriverConnectInterceptor3_0 implements AroundInterceptor {

    private final PLogger logger = PLoggerFactory.getLogger(this.getClass());
    private final boolean isDebug = logger.isDebugEnabled();

    public MongoDriverConnectInterceptor3_0() {
    }

    @Override
    public void before(Object target, Object[] args) {
        if (isDebug) {
            logBeforeInterceptor(target, args);
        }
    }

    @Override
    public void after(Object target, Object[] args, Object result, Throwable throwable) {
        if (isDebug) {
            logAfterInterceptor(target, args, result, throwable);
        }

        final boolean success = InterceptorUtils.isSuccess(throwable);
        // Must not check if current transaction is trace target or not. Connection can be made by other thread.

        if (success) {
            if (args == null) {
                return;
            }

            final List<String> hostList = getHostList(args[0]);
            String readPreference = getReadPreference(args[1]);
            String writeConcern = getWriteConcern(args[1]);

            DatabaseInfo databaseInfo = createDatabaseInfo(hostList, readPreference, writeConcern);
            if (databaseInfo == null) {
                databaseInfo = UnKnownDatabaseInfo.INSTANCE;
            }

            if (target instanceof DatabaseInfoAccessor) {
                ((DatabaseInfoAccessor) target)._$PINPOINT$_setDatabaseInfo(databaseInfo);
            }
        }
    }

    private void logBeforeInterceptor(Object target, Object[] args) {
        logger.beforeInterceptor(target, args);
    }

    private void logAfterInterceptor(Object target, Object[] args, Object result, Throwable throwable) {
        logger.afterInterceptor(target, args, result, throwable);
    }

    private DatabaseInfo createDatabaseInfo(List<String> hostList, String readPreference, String writeConcern) {

        DatabaseInfo databaseInfo = new MongoDatabaseInfo(MongoConstants.MONGODB, MongoConstants.MONGO_EXECUTE_QUERY,
                null, null, hostList, null, null, readPreference, writeConcern);

        if (isDebug) {
            logger.debug("parse DatabaseInfo:{}", databaseInfo);
        }

        return databaseInfo;
    }

    private List<String> getHostList(Object arg) {
        if (!(arg instanceof Cluster)) {
            return Collections.emptyList();
        }

        final Cluster cluster = (Cluster) arg;

        final List<String> hostList = new ArrayList<String>();

        Collection<ServerDescription> serverDescriptions;// = cluster.getDescription().getAll();//.getServerDescriptions();

        try {
            ClusterDescription.class.getDeclaredMethod("getServerDescriptions");
            serverDescriptions = cluster.getDescription().getServerDescriptions();
        } catch (NoSuchMethodException e) {
            serverDescriptions = cluster.getDescription().getAll();
        }

        for (ServerDescription serverDescription : serverDescriptions) {

            ServerAddress serverAddress = serverDescription.getAddress();
            final String hostAddress = HostAndPort.toHostAndPortString(serverAddress.getHost(), serverAddress.getPort());
            hostList.add(hostAddress);
        }

        return hostList;
    }

    private String getReadPreference(Object arg) {
        if (!(arg instanceof MongoClientOptions)) {
            return null;
        }

        final MongoClientOptions mongoClientOptions = (MongoClientOptions) arg;

        return mongoClientOptions.getReadPreference().getName();
    }

    private String getWriteConcern(Object arg) {
        if (!(arg instanceof MongoClientOptions)) {
            return null;
        }

        final MongoClientOptions mongoClientOptions = (MongoClientOptions) arg;

        return MongoUtil.getWriteConcern0(mongoClientOptions.getWriteConcern());
    }
}
