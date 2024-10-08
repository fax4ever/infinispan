package org.infinispan.server.router.configuration.builder;

import java.util.Collections;

import javax.net.ssl.SSLContext;

import org.infinispan.commons.configuration.attributes.AttributeSet;
import org.infinispan.server.core.configuration.IpFilterConfiguration;
import org.infinispan.server.core.configuration.ProtocolServerConfiguration;
import org.infinispan.server.core.configuration.SslConfigurationBuilder;
import org.infinispan.server.router.configuration.HotRodRouterConfiguration;
import org.infinispan.server.router.configuration.SinglePortRouterConfiguration;
import org.infinispan.server.router.logging.Log;

/**
 * Configuration builder for Single Port.
 *
 * @author Sebastian Łaskawiec
 */
public class SinglePortRouterBuilder extends AbstractRouterBuilder {

    private int sendBufferSize = 0;
    private int receiveBufferSize = 0;
    private String name = "single-port";
    private SSLContext sslContext;

    /**
     * Creates new {@link SinglePortRouterBuilder}.
     *
     * @param parent Parent {@link ConfigurationBuilderParent}
     */
    public SinglePortRouterBuilder(ConfigurationBuilderParent parent) {
        super(parent);
    }

    /**
     * Builds {@link HotRodRouterConfiguration}.
     */
    public SinglePortRouterConfiguration build() {
        if (this.enabled) {
            try {
                validate();
            } catch (Exception e) {
                throw Log.SERVER.configurationValidationError(e);
            }
            SslConfigurationBuilder sslConfigurationBuilder = new SslConfigurationBuilder(null);
            if (sslContext != null) {
                sslConfigurationBuilder.sslContext(sslContext).enable();
            }
            AttributeSet attributes = SinglePortRouterConfiguration.attributeDefinitionSet();
            attributes.attribute(ProtocolServerConfiguration.NAME).set(name);
            attributes.attribute(ProtocolServerConfiguration.HOST).set(ip.getHostName());
            attributes.attribute(ProtocolServerConfiguration.PORT).set(port);
            attributes.attribute(ProtocolServerConfiguration.IDLE_TIMEOUT).set(100);
            attributes.attribute(ProtocolServerConfiguration.RECV_BUF_SIZE).set(receiveBufferSize);
            attributes.attribute(ProtocolServerConfiguration.SEND_BUF_SIZE).set(sendBufferSize);
            return new SinglePortRouterConfiguration(attributes.protect(), sslConfigurationBuilder.create(), new IpFilterConfiguration(Collections.emptyList()));
        }
        return null;
    }

    /**
     * Sets Send buffer size
     *
     * @param sendBufferSize Send buffer size, must be greater than 0.
     */
    public SinglePortRouterBuilder sendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
        return this;
    }

    /**
     * Sets Receive buffer size.
     *
     * @param receiveBufferSize Receive buffer size, must be greater than 0.
     */
    public SinglePortRouterBuilder receiveBufferSize(int receiveBufferSize) {
        this.receiveBufferSize = receiveBufferSize;
        return this;
    }

    /**
     * Sets this server name.
     *
     * @param name The name of the server.
     */
    public SinglePortRouterBuilder name(String name) {
        this.name = name;
        return this;
    }

    public SinglePortRouterBuilder sslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    @Override
    protected void validate() {
        super.validate();
        if (receiveBufferSize < 0) {
            throw new IllegalArgumentException("Receive buffer size can not be negative");
        }
        if (sendBufferSize < 0) {
            throw new IllegalArgumentException("Send buffer size can not be negative");
        }
    }
}
