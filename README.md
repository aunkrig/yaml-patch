# YAML-PATCH

Yaml-patch is a 100% pure Java command line tool that can be used to modify YAML documents.

Since the document is first parsed, then manipulated and eventually stored, the original structure of the text file wrt/ white-space and comments is lost.

## Usage
    
<code>**yamlpatch** [ *option* ... ]</code><br>
Parse a YAML document from STDIN, modify it, and print it to STDOUT.

<code>**yamlpatch** [ *option* ... ] !*yaml-document*</code><br>
Parse the literal <YAML-document>, modify it, and print it to STDOUT.

<code>**yamlpatch** [ *option* ] *file-or-dir*</code><br>
Transforms *file-or-dir* in-place.

<code>**yamlpatch** [ *option* ] *file-or-dir* *new-file-or-dir*</code><br>
Read the YAML documents in *file-or-dir*, modify them, and write them to *new-file-or-dir*.

<code>**yamlpatch** [ *option* ] *file-or-dir* ... *existing-dir*</code><br>
Read the YAML documents in *file-or-dir*, modify them, and write them to files/dirs in *existing-dir*.

## Options

<code>**--help**</code><br>
Print this text and terminate.</dd>

<code>**--flow-style** **FLOW**|**BLOCK**|**AUTO**</code><br>
Output formatting; FLOW=single line, BLOCK=each item one a separate line, AUTO: in between

<code>**--keep-originals**</code><br>
If existing files would be overwritten, keep copies of the originals

<code>**--set** [ **--existing** | **--non-existing** ] *spec* ( *yaml-document* | **@**_file-name_ )</code><br>
Puts an entry into a set, or changes one sequence element.

<code>**--remove** [ **--existing** ] *spec*</code><br>
Removes one sequence element, map entry or set member.

<code>**--insert** *spec* ( *yaml-document* | **@**_file-name_ )</code><br>
Inserts an element into an sequence.

<code>**--add** *spec*</code><br>
Adds a member to a set.

## Specs

Many of the options specify a path from the root of the YAML document to a node, as follows:

<code>**.**_identifier_</code><br>
<code>**.(**_yaml-document_**)**</code><br>
Use the given map entry.

<code>**[**_0...sequenceSize-1_**]**</code><br>
Use the sequence element with the given index index <n>.

<code>**[**_-sequenceSize...-1_**]**</code><br>
Use the sequence element with the given index plus _sequenceSize_.

<code>**{**_yaml-document_**}**</code><br>
Use the given set member.
