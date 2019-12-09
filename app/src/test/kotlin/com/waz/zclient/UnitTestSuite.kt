package com.waz.zclient

import com.waz.zclient.user.data.mapper.UserEntityMapperTest
import com.waz.zclient.user.data.repository.UserRepositoryTest
import com.waz.zclient.user.data.source.remote.UserRemoteDataSourceTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    UserEntityMapperTest::class,
    UserRemoteDataSourceTest::class,
    UserRepositoryTest::class
)
class UnitTestSuite
