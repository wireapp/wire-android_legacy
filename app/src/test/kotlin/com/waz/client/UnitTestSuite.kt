package com.waz.client

import com.waz.client.user.data.mapper.UserEntityMapperTest
import com.waz.client.user.data.repository.UserRepositoryImplTest
import com.waz.client.user.data.source.remote.UserRemoteDataSourceImplTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    UserEntityMapperTest::class,
    UserRemoteDataSourceImplTest::class,
    UserRepositoryImplTest::class
)
class UnitTestSuite
