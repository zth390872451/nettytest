package com.server.websocket;

import com.service.handler.WebSocketServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * @creator ZTH
 * @modifier ZTH
 * @date 2017-07-03
 */
public class WebSocketServer {

	public void run(int port)throws  Exception{
		EventLoopGroup bossGroup = new NioEventLoopGroup();
		EventLoopGroup workerGroup = new NioEventLoopGroup();
		try {
			ServerBootstrap serverBootstrap = new ServerBootstrap();
			serverBootstrap.group(bossGroup,workerGroup)
					.channel(NioServerSocketChannel.class)
					.handler(new LoggingHandler(LogLevel.INFO))
					.childHandler(new ChannelInitializer<SocketChannel>() {
						protected void initChannel(SocketChannel ch) throws Exception {
							ChannelPipeline channelPipeline = ch.pipeline();
							//HttpServerCodec：HTTP解码器。可能会将一个HTTP请求解析成多个消息对象。
							//HttpServerCodec的作用是将请求或者应答消息按照HTTP协议的格式进行解码或者编码。
							channelPipeline.addLast("HTTP解码器",new HttpServerCodec());
							//HttpObjectAggregator 将多个消息转换为单一的一个FullHttpRequest
							//HttpObjectAggregator的目的是将HTTP消息的多个部分组合成一条完整的HTTP消息，也就是处理粘包与解包问题。
							channelPipeline.addLast("消息聚合器",
									new HttpObjectAggregator(65536));
							// ChunkedWriteHandler 用来向客户单发送HTML文件，它主要用于支持浏览器和服务端进行WebSocket通信
							channelPipeline.addLast("文件接收处理器",
									new ChunkedWriteHandler());
							//WebSocketServerHandler是我们自己编写的处理webscoket请求的ChannelHandler。
							channelPipeline.addLast("WebSocket协议请求处理器",
									new WebSocketServerHandler());
						}
					});
			Channel channel = serverBootstrap.bind(port).sync().channel();
			System.out.println("Web socket server started at port " + port
					+ '.');
			System.out
					.println("Open your browser and navigate to http://localhost:"
							+ port + '/');
			channel.closeFuture().sync();
		}finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}

	/**
	 * 启动netty服务器
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		int port = 8080;
		if (args.length > 0) {
			try {
				port = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
		}
		new WebSocketServer().run(port);
	}

}
