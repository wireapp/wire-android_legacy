package com.waz.zclient.shared.user.name

import com.waz.zclient.UnitTest
import com.waz.zclient.eq
import com.waz.zclient.shared.user.UsersRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
class ChangeNameUseCaseTest : UnitTest() {

    private lateinit var changeNameUseCase: ChangeNameUseCase

    @Mock
    private lateinit var userRepository: UsersRepository

    @Mock
    private lateinit var changeNameParams: ChangeNameParams

    @Before
    fun setup() {
        changeNameUseCase = ChangeNameUseCase(userRepository)
    }

    @Test
    fun `Given update name use case is executed, then the repository should update name`() = runBlockingTest {
        `when`(changeNameParams.newName).thenReturn(TEST_NAME)

        changeNameUseCase.run(changeNameParams)

        verify(userRepository).changeName(eq(TEST_NAME))
    }

    companion object {
        private const val TEST_NAME = "Test name"
    }

}
