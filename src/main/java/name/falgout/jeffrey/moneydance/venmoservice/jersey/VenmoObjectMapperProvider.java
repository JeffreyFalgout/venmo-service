package name.falgout.jeffrey.moneydance.venmoservice.jersey;

import javax.ws.rs.ext.ContextResolver;

import com.fasterxml.jackson.databind.ObjectMapper;

import name.falgout.jeffrey.moneydance.venmoservice.jackson.VenmoModule;

public class VenmoObjectMapperProvider implements ContextResolver<ObjectMapper> {
  private final ObjectMapper mapper;

  public VenmoObjectMapperProvider() {
    mapper = new ObjectMapper();
    mapper.registerModule(new VenmoModule());
  }

  @Override
  public ObjectMapper getContext(Class<?> type) {
    return mapper;
  }
}