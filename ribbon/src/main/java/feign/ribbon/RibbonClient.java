/**
 * Copyright 2012-2020 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.ribbon;

import com.netflix.client.ClientException;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import feign.Client;
import feign.Request;
import feign.Response;
import java.io.IOException;
import java.net.URI;

/**
 * RibbonClient can be used in Feign builder to activate smart routing and resiliency capabilities
 * provided by Ribbon. Ex.
 * 
 * <pre>
 * MyService api = Feign.builder.client(RibbonClient.create()).target(MyService.class,
 *     &quot;http://myAppProd&quot;);
 * </pre>
 * 
 * Where {@code myAppProd} is the ribbon client name and {@code myAppProd.ribbon.listOfServers}
 * configuration is set.
 */
public class RibbonClient implements Client {

  private final Client delegate; /* 默认：Client.Default */
  private final LBClientFactory lbClientFactory; /* 默认：LBClientFactory.Default, 这个工厂构建的Client是纯RB体系的*/


  public static RibbonClient create() {
    return builder().build();
  }

  public static Builder builder() { /* 简单构建一下delegate、lbClientFactory */
    return new Builder();
  }

  /**
   * @deprecated Use the {@link RibbonClient#create()}
   */
  @Deprecated
  public RibbonClient() {
    this(new Client.Default(null, null));
  }

  /**
   * @deprecated Use the {@link RibbonClient#create()}
   */
  @Deprecated
  public RibbonClient(Client delegate) {
    this(delegate, new LBClientFactory.Default());
  }

  RibbonClient(Client delegate, LBClientFactory lbClientFactory) {
    this.delegate = delegate;
    this.lbClientFactory = lbClientFactory;
  }

  @Override
  public Response execute(Request request, Request.Options options) throws IOException {
    try { /* 内部使用了RB体系的*/
      URI asUri = URI.create(request.url());
      String clientName = asUri.getHost();
      URI uriWithoutHost = cleanUrl(request.url(), clientName);
      LBClient.RibbonRequest ribbonRequest = /* rb的Request*/
          new LBClient.RibbonRequest(delegate, request, uriWithoutHost);
      return lbClient(clientName).executeWithLoadBalancer(ribbonRequest, /* 每个clientname对应一个rb体系的 lbclient */
          new FeignOptionsClientConfig(options)).toResponse();
    } catch (ClientException e) {
      propagateFirstIOException(e);
      throw new RuntimeException(e);
    }
  }

  static void propagateFirstIOException(Throwable throwable) throws IOException {
    while (throwable != null) {
      if (throwable instanceof IOException) {
        throw (IOException) throwable;
      }
      throwable = throwable.getCause();
    }
  }

  static URI cleanUrl(String originalUrl, String host) {
    return URI.create(originalUrl.replaceFirst(host, ""));
  }

  private LBClient lbClient(String clientName) { /* 构造 rb client*/
    return lbClientFactory.create(clientName);
  }

  static class FeignOptionsClientConfig extends DefaultClientConfigImpl {

    public FeignOptionsClientConfig(Request.Options options) {
      setProperty(CommonClientConfigKey.ConnectTimeout, options.connectTimeoutMillis());
      setProperty(CommonClientConfigKey.ReadTimeout, options.readTimeoutMillis());
      setProperty(CommonClientConfigKey.FollowRedirects, options.isFollowRedirects());
    }

    @Override
    public void loadProperties(String clientName) {

    }

    @Override
    public void loadDefaultValues() {

    }

  }

  public static final class Builder {

    Builder() {}

    private Client delegate;
    private LBClientFactory lbClientFactory;

    public Builder delegate(Client delegate) {
      this.delegate = delegate;
      return this;
    }

    public Builder lbClientFactory(LBClientFactory lbClientFactory) {
      this.lbClientFactory = lbClientFactory;
      return this;
    }

    public RibbonClient build() {
      return new RibbonClient(
          delegate != null ? delegate : new Client.Default(null, null),
          lbClientFactory != null ? lbClientFactory : new LBClientFactory.Default());
    }
  }
}
