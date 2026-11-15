# In the template's CMakeLists.txt, after the mod target is created, add:
#   include(cmake/modhost.cmake)
#   modhost_attach(<your mod target>)
function(modhost_attach target)
  file(GLOB MODHOST_SRC CONFIGURE_DEPENDS "${CMAKE_SOURCE_DIR}/src/bridge/*.cpp")
  target_sources(${target} PRIVATE ${MODHOST_SRC})
  target_include_directories(${target} PRIVATE "${CMAKE_SOURCE_DIR}/src")
  target_link_libraries(${target} PRIVATE log dl)
endfunction()
