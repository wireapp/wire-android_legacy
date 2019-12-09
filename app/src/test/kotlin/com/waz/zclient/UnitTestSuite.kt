package com.waz.zclient

import com.waz.zclient.user.data.mapper.UserEntityMapperTest
import com.waz.zclient.user.data.repository.UserRepositoryImplTest
import com.waz.zclient.user.data.source.remote.UserRemoteDataSourceImplTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    UserEntityMapperTest::class,
    UserRemoteDataSourceImplTest::class,
    UserRepositoryImplTest::class
)
class UnitTestSuite
