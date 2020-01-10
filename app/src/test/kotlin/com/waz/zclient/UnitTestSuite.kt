package com.waz.zclient

import com.waz.zclient.user.data.UserRepositoryTest
import com.waz.zclient.user.data.mapper.UserMapperTest
import com.waz.zclient.user.data.source.local.UserLocalDataSourceTest
import com.waz.zclient.user.data.source.remote.UserRemoteDataSourceTest
import kotlinx.coroutines.InternalCoroutinesApi
import org.junit.runner.RunWith
import org.junit.runners.Suite

@UseExperimental(InternalCoroutinesApi::class)
@RunWith(Suite::class)
@Suite.SuiteClasses(
    UserMapperTest::class,
    UserRemoteDataSourceTest::class,
    UserLocalDataSourceTest::class,
    UserRepositoryTest::class
)
class UnitTestSuite
