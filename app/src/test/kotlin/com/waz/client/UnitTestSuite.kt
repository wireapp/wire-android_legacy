package com.waz.client

import com.waz.client.user.data.model.UserEntityTest
import com.waz.client.user.data.repository.UserRepositoryImplTest
import com.waz.client.user.data.source.remote.UserRemoteDataSourceImplTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    UserEntityTest::class,
    UserRemoteDataSourceImplTest::class,
    UserRepositoryImplTest::class

)
class UnitTestSuite
