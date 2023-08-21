# YAML-PATCH

Yaml-patch is a 100% pure Java command line tool that can be used to modify YAML documents.

yaml-patch preserves all comments in the document; optionally it also creates comments for elements that removed or changed.

FOr the most up-to-date documentation, check the `--help` page of the tool: [yaml-patch command line help](https://aunkrig.github.io/yaml-patch/Main.main(String[]).html)

## Quick Start

Manipulating objects:

    yamlpatch --set .path.to.key value                 # Add new or change existing object member
    yamlpatch --set --existing .path.to.key value      # Change existing object member
    yamlpatch --set --non-existing .path.to.key value  # Add new object member
    yamlpatch --set --comment .path.to.key value       # Also add a comment with the previous key and value
    yamlpatch --set --prepend-map .path.to.key value   # Add a new map entry at the beginning (instead of to the end)

    yamlpatch --remove .path.to.key            # Remove object member (if the key exists)
    yamlpatch --remove --existing .path.to.key # Remove existing object member
    yamlpatch --remove --comment .path.to.key  # Also add a comment with the old key and value

Manipulating sequences:

    yamlpatch --set .path.to.sequence[7] value                # Change sequence element number 7 (or, iff the current length of the sequence is 7, append a new element)
    yamlpatch --set --existing .path.to.sequence[7] value     # Change sequence element number 7
    yamlpatch --set --non-existing .path.to.sequence[7] value # Append a new element (current length of the sequence must be 7)
    yamlpatch --set .path.to.sequence[] value                 # Append a new element
    yamlpatch --set .path.to.sequence[-2] value               # Change the next-to-last element (sequence must contain two or more elements)

    yamlpatch --remove .path.to.sequence[3]   # Remove object member (if the key exists)
    yamlpatch --remove --comment .path.to.key # Also add a comment with the old element

Manipulating sets:

    yamlpatch --add .path.to.set(member)                # Add set member if it does not yet exist
    yamlpatch --set --non-existing .path.to.set(member) # Add new set member
    yamlpatch --set --prepend-map .path.to.set(member)  # Add set member at the beginning (instead of to the end)

    yamlpatch --remove .path.to.set(member)            # Remove set member (if the key exists)
    yamlpatch --remove --existing .path.to.set(member) # Remove existing set member
    yamlpatch --remove --comment .path.to.set(member)  # Also add a comment with the old member

