import javafx.util.Pair;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

public class Server {
    static ArrayList<ByteBuffer> messages = new ArrayList<>();
    static ArrayList<Pair<String, String>> clientsPersistent = new ArrayList<>();
    static ArrayList<Pair<String, String>> clients = new ArrayList<>();

    @SuppressWarnings("unused")
    public Server() throws IOException, InterruptedException {
        InetAddress hostIp = InetAddress.getLocalHost();
        Scanner input = new Scanner(System.in);

        System.out.println("Port: ");
        int port = input.nextInt();
        input.close();

        Selector selector = Selector.open();
        ServerSocketChannel ssChannel = ServerSocketChannel.open();
        ssChannel.configureBlocking(false);

        ssChannel.bind(new InetSocketAddress(hostIp, port));
        SelectionKey selecKey = ssChannel.register(selector, ssChannel.validOps(), null);

        System.out.println("Started successfully on " + hostIp +  ", waiting for connections!\n");
        while (true) {
            selector.select();

            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = keys.iterator();

            while (keyIterator.hasNext()){
                SelectionKey currentKey = keyIterator.next();

                //Connection from client
                if (currentKey.isAcceptable()){
                    SocketChannel client = ssChannel.accept();
                    client.configureBlocking(false);
                    client.register(selector, client.validOps());


                    System.out.println(getClientAddress(client) + " is connected");
                }
                //Read from client
                else if (currentKey.isReadable()){
                    SocketChannel client = (SocketChannel) currentKey.channel();
                    try {
                        ByteBuffer buffer = ByteBuffer.allocate(256);
                        client.read(buffer);
                        String msg = new String(buffer.array()).trim();

                        String clientAdd = getClientAddress(client);

                        if (!clientPairExists(client)){
                            if (msg.equals("")){
                                msg = clientAdd;
                            }
                            clientsPersistent.add(new Pair<>(msg, clientAdd));
                            clients.add(new Pair<>(msg, clientAdd));
                            System.out.println("Username '" + msg + "' registered to " + getClientAddress(client));
                        }
                        else {
                            if (msg.equals("exit")){
                                System.out.println(getClientAddress(client) + " has disconnected");
                                removeClient(client);
                            }

                            if (!msg.equals("") && !msg.equals("exit")){
                                messages.add(ByteBuffer.wrap((getClientName(client) + ": " + msg).getBytes()));
                                clients.remove(getClientPair(client));
                                System.out.println(getClientName(client) + ": " + msg);
                            }
                        }
                    }
                    catch (IOException e){
                        System.out.println("Error connection to " + getClientAddress(client) + " was forcibly closed!");
                        removeClient(client);
                    }
                }
                if (currentKey.isWritable() && !messages.isEmpty()){
                    SocketChannel client = (SocketChannel) currentKey.channel();
                    Pair<String, String> p = getClientPair(client);

                    if (clients.contains(p)){
                        client.write(messages.get(0));
                        clients.remove(p);
                    }
                    else if (clients.isEmpty()){
                        messages.clear();
                        clients.addAll(clientsPersistent);
                    }
                    Thread.sleep(33);
                }
                keyIterator.remove();
            }
        }
    }

    public static String getClientAddress(SocketChannel client){
        return String.valueOf(client.socket().getRemoteSocketAddress().toString().replace("/", ""));
    }

    public static Pair getClientPair(SocketChannel client){
        String add = getClientAddress(client);
        Pair<String, String> out = null;

        for (Pair<String,String> p : clientsPersistent){
            if (p.getValue().equals(add)){
                out = p;
            }
        }
        return out;
    }

    public static String getClientName(SocketChannel client){
        Pair<String,String> p = getClientPair(client);
        return p.getKey();
    }

    public static boolean clientPairExists (SocketChannel client){
        boolean exists = false;
        for (Pair<String, String> p : clientsPersistent){
            if (p.getValue().equals(getClientAddress(client))){
                exists = true;
            }
        }
        return exists;
    }

    public static void removeClient(SocketChannel client) throws IOException {
        Pair<String, String> p = getClientPair(client);
        clientsPersistent.remove(p);
        clients.remove(p);
        client.close();
    }
}
