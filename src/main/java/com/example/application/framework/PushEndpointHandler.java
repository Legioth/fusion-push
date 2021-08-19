package com.example.application.framework;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.googlecode.gentyref.GenericTypeReflector;

import reactor.core.publisher.Flux;

public class PushEndpointHandler extends TextWebSocketHandler {

    private final String mapping;
    private final Object endpoint;
    private final ObjectMapper objectMapper;
    private final Map<String, Method> endpointMethods = new HashMap<>();

    public PushEndpointHandler(String mapping, Object endpoint, ObjectMapper objectMapper) {
        this.mapping = mapping;
        this.endpoint = endpoint;
        this.objectMapper = objectMapper;

        for (Method method : endpoint.getClass().getMethods()) {
            if (isEndpontMethod(method)) {
                endpointMethods.put(method.getName(), method);
            }
        }

    }

    private boolean isEndpontMethod(Method method) {
        int modifiers = method.getModifiers();

        Class<?> declaringClass = method.getDeclaringClass();
        if (declaringClass == Object.class) {
            return false;
        }

        if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers)) {
            System.out.println("Ignoring non-public or static method " + method);
            return false;
        }

        if (!method.getReturnType().equals(Flux.class)) {
            System.out.println("Ignoring non-flux method " + method);
            return false;
        }

        return true;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String text = message.getPayload();

        JsonNode tree = objectMapper.readTree(text);

        String methodName = tree.get("method").asText();
        int id = tree.get("id").asInt();

        ArrayNode argsJson = (ArrayNode) tree.get("args");

        Method method = endpointMethods.get(methodName);

        Object[] args = decodeArgs(argsJson, method.getParameterTypes(), objectMapper);
        Flux<?> result = (Flux<?>) method.invoke(endpoint, args);

        result.subscribe(item -> {
            try {
                ObjectNode json = objectMapper.createObjectNode();

                JsonNode itemJson = objectMapper.valueToTree(item);
                json.put("id", id);
                json.set("item", itemJson);

                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(json)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, error -> {
            // TODO terminate client-side generator
            error.printStackTrace();
        }, () -> {
            // when done
            ObjectNode json = objectMapper.createObjectNode();
            json.put("id", id);
            json.put("done", true);

            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(json)));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });
    }

    private static Object[] decodeArgs(ArrayNode argsJson, Class<?>[] parameterTypes, ObjectMapper objectMapper)
            throws IOException {
        if (argsJson == null) {
            return new Object[0];
        }

        Object[] args = new Object[argsJson.size()];
        for (int i = 0; i < args.length; i++) {
            Object value = objectMapper.readerFor(parameterTypes[i]).readValue(argsJson.get(i));
            args[i] = value;
        }

        return args;
    }

    public void logCode() {
        if (endpointMethods.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("TS client generation is not implemented in this prototype, you need to manually create "
                + endpoint.getClass().getName() + ".ts with the following contents: \n\n");

        message.append("import { open, EndpointGenerator } from 'Frontend/fake-generated/pushclient';\n");

        endpointMethods.values().forEach(method -> {
            Class<?> returnType = getFluxParameterType(method.getGenericReturnType());

            String parameterNamesAndTypes = Stream.of(method.getParameters()).map(parameter -> {
                return parameter.getName() + ": " + getTsType(parameter.getType());
            }).collect(Collectors.joining(", "));

            String parameterNames = Stream.of(method.getParameters()).map(Parameter::getName)
                    .collect(Collectors.joining(", "));

            message.append("export function ").append(method.getName()).append('(').append(parameterNamesAndTypes)
                    .append("): EndpointGenerator<").append(getTsType(returnType)).append("> {\n");
            message.append("  return open('").append(mapping).append("', '").append(method.getName() + "', [")
                    .append(parameterNames).append("]);\n");
            message.append("}\n");

        });

        message.append("\n\n");

        Logger logger = LoggerFactory.getLogger(PushEndpointHandler.class);
        logger.info(message.toString());
    }

    private static Class<?> getFluxParameterType(Type type) {
        Type typeParameter = GenericTypeReflector.getTypeParameter(type, Flux.class.getTypeParameters()[0]);
        if (typeParameter instanceof Class<?>) {
            return (Class<?>) typeParameter;
        } else {
            return Object.class;
        }
    }

    private static String getTsType(Class<?> type) {
        if (type == int.class) {
            return "number";
        } else if (type == String.class) {
            return "string";
        } else {
            return "any";
        }
    }
}
