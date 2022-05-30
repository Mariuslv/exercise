import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Test {
    public static void main(String[] args) throws Exception{
        HttpServer server=new HttpServer(80);
        server.runServer();
    }
}

class HttpServer{
    private final int port;
    private final Map<String,String> uploadedFiles;

    public HttpServer(int port){
        this.port=port;
        this.uploadedFiles=new HashMap<>();
    }

    public void runServer() throws Exception{
        EventLoopGroup boss=new NioEventLoopGroup(1);
        EventLoopGroup worker= new NioEventLoopGroup();
        try {
            ServerBootstrap serverBootstrap=new ServerBootstrap();
            serverBootstrap.group(boss,worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<NioSocketChannel>() {
                        @Override
                        protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                            nioSocketChannel.pipeline().addLast("http-decoder",new HttpRequestDecoder())
                                    .addLast("http-encoder",new HttpResponseEncoder())
                                    .addLast("Aggregator",new HttpObjectAggregator(65536))
                                    .addLast("FileServer",new FileServerHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG,128)
                    .childOption(ChannelOption.SO_KEEPALIVE,Boolean.TRUE);

            ChannelFuture f = serverBootstrap.bind(this.port).sync();
            System.out.println("Server started,"+serverBootstrap);
            f.channel().closeFuture().sync();
        }finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    class FileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest fullReq) throws Exception {
            Map<String,String> requstParams = new HashMap<>();
            boolean valid=true;
            String msg="";
            HttpMethod method= fullReq.method();

            if(HttpMethod.GET==method){
                QueryStringDecoder decoder=new QueryStringDecoder(fullReq.uri());
                String requestPath=decoder.path();
                for(Map.Entry<String,List<String>> e: decoder.parameters().entrySet()) {
                    requstParams.put(e.getKey(), e.getValue().get(0));
                    requstParams.put("RequestPath", requestPath);
                }
                msg=uploadedFiles.keySet().toString();

            }else if(HttpMethod.POST == method || HttpMethod.PUT == method){
                HttpPostRequestDecoder decoder=new HttpPostRequestDecoder(fullReq);
                decoder.offer(fullReq);
                List<InterfaceHttpData> params= decoder.getBodyHttpDatas();
                for (InterfaceHttpData p: params){
                    if(p instanceof Attribute){
                        Attribute data = (Attribute) p;
                        requstParams.put(data.getName(),data.getValue());
                    }
                    else {
                        FileUpload file= (FileUpload) p;
                        uploadedFiles.put(file.getName(),file.getString());
                    }
                }
                msg="File uploaded";

            }else if (HttpMethod.DELETE==method){
                QueryStringDecoder decoder=new QueryStringDecoder(fullReq.uri());
                String requestPath=decoder.path();
                for(Map.Entry<String,List<String>> e: decoder.parameters().entrySet()) {
                    requstParams.put(e.getKey(), e.getValue().get(0));
                    requstParams.put("RequestPath", requestPath);
                }
                String delFileName=requstParams.get("fileName");
                uploadedFiles.remove(delFileName);
                msg="File deleted";

            }else {
                valid=false;
            }

            writeResponse(ctx,valid,msg);
        }

        protected void writeResponse(ChannelHandlerContext ctx,boolean valid,String msg) throws Exception {
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK, Unpooled.wrappedBuffer(msg.getBytes(StandardCharsets.UTF_8)));

            //set headers
            HttpHeaders heads = response.headers();
            heads.add(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes()); // 3
            heads.add(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

            ctx.writeAndFlush(response);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            System.out.println("exceptionCaught");
            if(null != cause) cause.printStackTrace();
            if(null != ctx) ctx.close();
        }
    }
}