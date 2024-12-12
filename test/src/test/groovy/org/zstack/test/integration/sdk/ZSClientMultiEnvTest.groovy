package org.zstack.test.integration.sdk

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.zstack.core.Platform
import org.zstack.header.message.APIMessage
import org.zstack.header.rest.RESTFacade
import org.zstack.sdk.*
import org.zstack.test.integration.ZStackTest
import org.zstack.testlib.EnvSpec
import org.zstack.testlib.SubCase
import org.zstack.utils.gson.JSONObjectUtil

class ZSClientMultiEnvTest extends SubCase {
    EnvSpec env

    @Override
    void setup() {
        useSpring(ZStackTest.springSpec)
    }

    @Override
    void environment() {
        env = env {
            instanceOffering {
                name = "instanceOffering"
                memory = SizeUnit.GIGABYTE.toByte(1)
                cpu = 1
            }
        }
    }

    @Override
    void test() {
        env.create {
            testMultipleEnvironments()
            testEnvironmentSwitching()
            testInvalidEnvironment()
        }
    }

    void testMultipleEnvironments() {
        // Create test environments
        ZSEnvironment env1 = new ZSEnvironment.Builder()
            .setName("env1")
            .setHostname("localhost")
            .setPort(8081)
            .build()

        ZSEnvironment env2 = new ZSEnvironment.Builder()
            .setName("env2")
            .setHostname("localhost")
            .setPort(8082)
            .build()

        // Configure ZSClient with multiple environments
        ZSConfig config = new ZSConfig.Builder()
            .addEnvironment(env1)
            .addEnvironment(env2)
            .build()

        ZSClient.configure(config)

        // Verify environments are properly configured
        Assert.assertEquals(env1, ZSClient.getConfig().getCurrentEnvironment())
        Assert.assertEquals(env1, ZSClient.getConfig().getEnvironment("env1"))
        Assert.assertEquals(env2, ZSClient.getConfig().getEnvironment("env2"))
    }

    void testEnvironmentSwitching() {
        // Create test environments
        ZSEnvironment env1 = new ZSEnvironment.Builder()
            .setName("env1")
            .setHostname("localhost")
            .setPort(8081)
            .setReadTimeout(5000L)
            .build()

        ZSEnvironment env2 = new ZSEnvironment.Builder()
            .setName("env2")
            .setHostname("localhost")
            .setPort(8082)
            .setReadTimeout(10000L)
            .build()

        // Configure ZSClient
        ZSConfig config = new ZSConfig.Builder()
            .addEnvironment(env1)
            .addEnvironment(env2)
            .build()

        ZSClient.configure(config)

        // Test environment switching
        Assert.assertEquals("env1", ZSClient.getConfig().getCurrentEnvironment().getName())
        
        ZSClient.setCurrentEnvironment("env2")
        Assert.assertEquals("env2", ZSClient.getConfig().getCurrentEnvironment().getName())
        Assert.assertEquals(8082, ZSClient.getConfig().getCurrentEnvironment().getPort())
        
        ZSClient.setCurrentEnvironment("env1")
        Assert.assertEquals("env1", ZSClient.getConfig().getCurrentEnvironment().getName())
        Assert.assertEquals(8081, ZSClient.getConfig().getCurrentEnvironment().getPort())
    }

    void testInvalidEnvironment() {
        // Create test environment
        ZSEnvironment env = new ZSEnvironment.Builder()
            .setName("env1")
            .setHostname("localhost")
            .setPort(8081)
            .build()

        // Configure ZSClient
        ZSConfig config = new ZSConfig.Builder()
            .addEnvironment(env)
            .build()

        ZSClient.configure(config)

        // Test switching to non-existent environment
        try {
            ZSClient.setCurrentEnvironment("non-existent")
            Assert.fail("Should throw IllegalArgumentException when switching to non-existent environment")
        } catch (IllegalArgumentException e) {
            // Expected exception
        }

        // Verify current environment remains unchanged
        Assert.assertEquals("env1", ZSClient.getConfig().getCurrentEnvironment().getName())
    }

    @Override
    void clean() {
        env.delete()
    }
}
