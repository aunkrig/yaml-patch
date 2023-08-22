# Yaml-patch

Yaml-patch is a 100% pure Java command line tool for modifying YAML documents.

Yaml-patch preserves all comments and formatting in the document; optionally it also creates comments for elements that were removed or changed.

## Documentation

For the most up-to-date documentation, check the `--help` page of the tool: [yaml-patch command line help](https://aunkrig.github.io/yaml-patch/Main.main(String[]).html)

## Quick Start

Manipulating objects:

    yamlpatch --set                .path.to.key value  # Add new or change existing object member
    yamlpatch --set --existing     .path.to.key value  # Change existing object member
    yamlpatch --set --non-existing .path.to.key value  # Add new object member
    yamlpatch --set --comment      .path.to.key value  # Also add a comment with the previous key and value
    yamlpatch --set --prepend-map  .path.to.key value  # Add a new map entry at the beginning (instead of to the end)

    yamlpatch --remove            .path.to.key  # Remove object member (if the key exists)
    yamlpatch --remove --existing .path.to.key  # Remove existing object member
    yamlpatch --remove --comment  .path.to.key  # Also add a comment with the old key and value

Manipulating sequences:

    yamlpatch --set                '.path.to.sequence[]'   value  # Append a new element
    yamlpatch --set                '.path.to.sequence[7]'  value  # Change 8th element (or, iff the current length of the sequence is 7, append a new element)
    yamlpatch --set --existing     '.path.to.sequence[7]'  value  # Change 8th element
    yamlpatch --set --non-existing '.path.to.sequence[7]'  value  # Append a new element (current length of the sequence must be 7)
    yamlpatch --set                '.path.to.sequence[-2]' value  # Change the next-to-last element (sequence must contain two or more elements)

    yamlpatch --remove           '.path.to.sequence[3]'       # Remove 4th element
    yamlpatch --remove           '.path.to.sequence[-2]'      # Remove next-to-last element (sequence must contain two or more elements)
    yamlpatch --remove           '.path.to.sequence.alpha'    # Remove sequence element by value
    yamlpatch --remove           '.path.to.sequence.({a: b})' # Remove sequence element by (complex) value
    yamlpatch --remove --comment '.path.to.sequence[3]'       # Also add a comment with the old element

Manipulating sets:

    path:
      to:
        set: !!set
          ? red
          ? green
          ? blue

    yamlpatch --add                '.path.to.set.brown'    # Add member "brown" if it does not yet exist
    yamlpatch --add --non-existing '.path.to.set.brown'    # Add new member "brown"
    yamlpatch --add --prepend      '.path.to.set.brown'    # Add member at the beginning (instead of to the end)
    yamlpatch --add                '.path.to.set.({a: b})' # Add a complex member

    yamlpatch --remove            .path.to.set.red  # Remove member "red" (if the key exists)
    yamlpatch --remove --existing .path.to.set.red  # Remove existing member "red"
    yamlpatch --remove --comment  .path.to.set.red  # Also add a comment with the removed member

