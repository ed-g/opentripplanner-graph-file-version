/*

  GraphFileVersion.java

  Find an OpenTripPlanner version string from Graph.obj file by
  searching its serialized data.

  Copyright 2013 Ed Groth
  http://github.com/ed-g

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.
  
  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.
  
  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.

  To compile/run using Leiningen: 
  lein javac
  java -cp target/classes GraphFileVersion /path/to/Graph.obj
  
  To compile/run using Java only:
  
  javac GraphFileVersion.java
  java GraphFileVersion /path/to/Graph.obj
*/


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import java.util.regex.Pattern;
import java.util.regex.Matcher;


public class GraphFileVersion {

    static int MINIMUM_STRING_LENGTH = 2;

    public static void usage () {
	System.err.println("Please use: GraphFileVersion Graph.obj");
	System.err.println("Where Graph.obj is an OpenTripPlanner graph file, and we will");
	System.err.println("attempt to find the version of OpenTripPlanner which created it.");
    }

    public static void exit(int return_value) {
	(java.lang.Runtime.getRuntime()).exit(return_value);
    }


    public static MappedByteBuffer open_mapped_byte_buffer_or_die (String file_name) {
	File file = new File(file_name);
	FileInputStream stream;
	FileChannel channel;
	
	// System.err.println ("graph file: " + file);
	try {
	    stream = new FileInputStream (file);
	    channel = stream.getChannel();
	    long file_size = channel.size();
	    MappedByteBuffer buffer = channel.map (FileChannel.MapMode.READ_ONLY,
						   0, file_size);
	    //System.err.println ("input stream: " + stream);
	    //System.err.println ("file size: " + file_size + " buffer: " + buffer);

	    return buffer;
	} catch (Exception e) {
	    System.out.println ("Could not read graph file, error was:");
	    System.out.println (e);
	    exit(2);
	}
	// Java requires this return statment, to compile.  In fact
	// this line will never be reached, since we exit if the buffer
	// cannot be opened.
	return null;
    }

    public static boolean is_printable (int c) {
	// same as isprint() in C.
	return c >= 32 && c <= 128;
    }

    public static int printable_string_raw_length (ByteBuffer buf, int pos) {
	// length of printable string in buf at pos
	long size = buf.limit ();
	int good_chars = 0;
	for (int i = pos; i < size; i++) {
	    int c = buf.get(i);
	    if (is_printable (c)) {
		good_chars++;
	    } else {
		// ran out of printable chars.
		return good_chars;
	    }
	}
	return good_chars;
    }

    public static int printable_string_encoded_length (ByteBuffer buf, int pos) {
	// length of printable string in buf at pos, according to 16-bit integer
	// immediately before pos.
	int size_pos = pos - 2;
	int size = 0;

	if (size_pos < 0)
	    return 0; // can't read past beginning of buffer.

	size = (256 * buf.get(size_pos)) + buf.get(size_pos +1);

	return size;
    }
 
    public static int printable_string_length (ByteBuffer buf, int pos) {
	int raw_length = printable_string_raw_length (buf, pos);
	int encoded_length = printable_string_encoded_length (buf, pos);

	//System.out.println("pos: " + pos + " encoded size: " + encoded_length + " printable size: " + raw_length );

	if (encoded_length <= raw_length) {
	    return encoded_length; // trust the encoded length more.
	} else {
	    // we may have found a printable string, but it's not part of a Java serialization.
	    // (or, the language is not English... Sorry!!!)
	    return 0;
	}
    }

    public static String printable_string_as_String (ByteBuffer buf, int pos) {
	int len = printable_string_length (buf,pos);
	if (len < 1)
	    return null;
	char [] chars = new char [len];
	for (int i = 0; i < len; i++)
	    chars [i] = (char) buf.get (pos + i);
	return (new String (chars));
    }

    public static int find_next_printable_string (ByteBuffer buf, int pos,
						   int how_long) {
	// Starting at pos, look for the next printable string in the buffer buf, and return
	// its position. Otherwise, if no string is found, return -1.
	long size = buf.limit();
	for (int i = pos; i < size; i++) {
	    if (printable_string_length (buf, i) > how_long) {
		return i; // found a string at pos.
	    }
	}
	return -1; // not found.
    }

    public static void find_locations_of_printable_strings (ByteBuffer buf) {
	// This function is handy for debugging, or as a skeleton for a more
	// specific search function. If only java had HOF we could simply add a predicate as an argument.
	// Maybe we could implment predicates as functions subclassing a class with matches() method.
	// Probably overkill for this application where we only need two search functions.
	int pos = 0; // start at the first character of buffer.
	for (;;) {
	    pos = find_next_printable_string(buf, pos, GraphFileVersion.MINIMUM_STRING_LENGTH);
	    if (pos < 0) { 
		return; // no string found
	    }
	    int len = printable_string_length (buf, pos);

	    System.out.println ("found a string at position: " + pos
				+ " that is " + len + " characters long.");
	    System.out.println ("printable length: " + printable_string_raw_length (buf, pos) 
				+ " encoded length: " + printable_string_encoded_length (buf, pos));
	    System.out.println ("string is: " + printable_string_as_String (buf, pos));
	    pos += len; // seek past the string.
	}
    }

    public static int find_maven_version_object (ByteBuffer buf) {
	int pos = 0; // Start at the first character of the buffer.

	for (;;) {
	    pos = find_next_printable_string(buf, pos, GraphFileVersion.MINIMUM_STRING_LENGTH);
	    if (pos < 0) return -1; // no more strings found
	    
	    String s = printable_string_as_String (buf, pos);
	    
	    if ("org.opentripplanner.common.MavenVersion".equals(s)) {
		//System.err.println("Found maven version string, at location " + pos + ".");
		return pos;
	    }
	    
	    int len = printable_string_length (buf, pos);
	    pos += len; // seek past the string.
	}
    }

    public static String find_git_commit_version (ByteBuffer buf, int pos) {
	for (;;) {
	    pos = find_next_printable_string(buf, pos, GraphFileVersion.MINIMUM_STRING_LENGTH);
	    if (pos < 0) return null; // no more strings found
	    
	    int len = printable_string_length (buf, pos);
	    
	    if (len == 40) {
		// git version is 40 hex characters.
		String s = printable_string_as_String (buf, pos);
		
		// verify that all are hex digits: 0-9, A-F.
		Pattern p = Pattern.compile("^[0-9a-fA-F]+$");
		Matcher m = p.matcher(s);
		
		if (m.find()) {
		    return s;
		}
	    }
	    pos += len; // seek past the string.
	}
    }


    public static String find_otp_text_version (ByteBuffer buf, int pos) {
	for (;;) {
	    pos = find_next_printable_string(buf, pos, GraphFileVersion.MINIMUM_STRING_LENGTH);
	    if (pos < 0) return null; // no more strings found
	    
	    int len = printable_string_length (buf, pos);
	    String s = printable_string_as_String (buf, pos);

	    // check if it matches "^[\d+]\.[\d+]\.[\d+]".
	    // there might or might not be an extraversion string after that, so we don't check.
	    Pattern p = Pattern.compile("^[\\d+]\\.[\\d+]\\.[\\d+]");
	    Matcher m = p.matcher(s);

	    if (m.find()) {
		return s;
	    }
	    pos += len; // seek past the string.
	}
    }
	

    public static void read_graph_file (String graph_file_name) {
	MappedByteBuffer buf = open_mapped_byte_buffer_or_die (graph_file_name);
	long file_size =  buf.limit();
	int maven_object_location = 0;

	//System.err.println ("graph file size: " + file_size);

	if (0 < (maven_object_location = find_maven_version_object (buf))) {
	    // found maven version object
	    try {
		String git_version = find_git_commit_version(buf, maven_object_location);
		String otp_text_version = find_otp_text_version(buf, maven_object_location);


		// This XML format is somewhat similar to the output of
		// http://otp.example.org//opentripplanner-api-webapp/ws/serverinfo
		// since that's almost certainly what the user will be comparing it against.
		System.out.println("<fileVersion>");
		System.out.println("<commit>" + git_version + "</commit>");
		System.out.println("<version>" +  otp_text_version +  "</version>");
		System.out.println("</fileVersion>");

		exit(0);
	    } catch (Exception e) {
		System.err.println("Sorry, was not able to find Graph.obj OpenTripPlanner version.");
		System.err.println("Which is strange since I did find a Maven version object.");
		System.err.println("Odds are the file format has changed, you may need to update this program.");
		exit(4);
	    }
	} else {
	    System.err.println("Sorry, was not able to find Graph.obj OpenTripPlanner version.");
	    exit(3);
	}

	// find_locations_of_printable_strings (buf);
    }

    public static void main (String [] args) {
	// System.out.println("number of args: " + args.length);
	if (args.length == 1) {
	    read_graph_file(args[0]);
	} else {
	    usage();
	    exit(1);
	}
    }
}
