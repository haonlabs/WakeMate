package id.haonlabs.wakemate.data.live

import org.eclipse.paho.client.mqttv3.MqttException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AuthRejectionTest {

    @Test
    fun `treats broker refusals as terminal`() {
        assertTrue(isAuthRejection(MqttException(MqttException.REASON_CODE_NOT_AUTHORIZED.toInt())))
        assertTrue(isAuthRejection(MqttException(MqttException.REASON_CODE_FAILED_AUTHENTICATION.toInt())))
    }

    @Test
    fun `still retries plain network failures`() {
        assertFalse(isAuthRejection(MqttException(MqttException.REASON_CODE_CLIENT_TIMEOUT.toInt())))
        assertFalse(isAuthRejection(MqttException(MqttException.REASON_CODE_CONNECTION_LOST.toInt())))
        assertFalse(isAuthRejection(IOException("jaringan putus")))
    }

    @Test
    fun `looks through a wrapped cause`() {
        val refused = MqttException(MqttException.REASON_CODE_NOT_AUTHORIZED.toInt())
        assertTrue(isAuthRejection(MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION.toInt(), refused)))
    }
}