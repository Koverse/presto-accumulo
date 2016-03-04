/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bloomberg.presto.accumulo.conf;

import bloomberg.presto.accumulo.serializers.AccumuloRowSerializer;
import bloomberg.presto.accumulo.serializers.LexicoderRowSerializer;
import bloomberg.presto.accumulo.serializers.StringRowSerializer;
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.StandardErrorCode;
import com.facebook.presto.spi.session.PropertyMetadata;
import com.facebook.presto.spi.type.VarcharType;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hadoop.io.Text;

import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.facebook.presto.spi.session.PropertyMetadata.booleanSessionProperty;
import static com.facebook.presto.spi.session.PropertyMetadata.stringSessionProperty;

/**
 * Class contains all table properties for the Accumulo connector. Used when creating a table:
 * <br>
 * <br>
 * CREATE TABLE foo (a VARCHAR, b INT)
 * WITH (column_mapping = 'b:md:b', internal = true);
 */
public final class AccumuloTableProperties
{
    private static final String COLUMN_MAPPING = "column_mapping";
    private static final String INDEX_COLUMNS = "index_columns";
    private static final String EXTERNAL = "external";
    private static final String LOCALITY_GROUPS = "locality_groups";
    private static final String ROW_ID = "row_id";
    private static final String SERIALIZER = "serializer";

    private final List<PropertyMetadata<?>> tableProperties;

    @Inject
    public AccumuloTableProperties(AccumuloConfig config)
    {
        PropertyMetadata<String> s1 = stringSessionProperty(COLUMN_MAPPING,
                "Comma-delimited list of column metadata: col_name:col_family:col_qualifier,[...]",
                null, false);

        PropertyMetadata<String> s2 =
                stringSessionProperty(INDEX_COLUMNS,
                        "A comma-delimited list of Presto columns that are indexed in this table's "
                                + "corresponding index table.  Default is no indexed columns.",
                        "", false);

        PropertyMetadata<Boolean> s3 = booleanSessionProperty(EXTERNAL,
                "If true, Presto will only do metadata operations for the table. Else, Presto will "
                + "create and drop Accumulo tables where appropriate. Default false.",
                false, false);

        PropertyMetadata<String> s4 = stringSessionProperty(LOCALITY_GROUPS,
                "List of locality groups to set on the Accumulo table.  String format is locality "
                        + "group name, colon, comma delimited list of column families in the group.  "
                        + "Groups are delimited by pipes.  Example: group1:famA,famB,famC|"
                        + "group2:famD,famE,famF|etc....  Default is no locality groups.",
                null, false);

        PropertyMetadata<String> s5 = stringSessionProperty(ROW_ID,
                "If true, a DROP TABLE statement WILL delete the corresponding Accumulo table. Default false.",
                null, false);

        PropertyMetadata<String> s6 =
                new PropertyMetadata<String>(SERIALIZER,
                        "Serializer for Accumulo data encodings. Can either be 'default', "
                                + "'string', 'lexicoder', or a Java class name. Default is 'default', i.e. "
                                + "the value from AccumuloRowSerializer.getDefault()",
                        VarcharType.VARCHAR, String.class,
                        AccumuloRowSerializer.getDefault().getClass().getName(), false,
                        x -> x.equals("default")
                                ? AccumuloRowSerializer.getDefault().getClass().getName()
                                : (x.equals("string") ? StringRowSerializer.class.getName()
                                        : (x.equals("lexicoder")
                                                ? LexicoderRowSerializer.class.getName()
                                                : (String) x)));

        tableProperties = ImmutableList.of(s1, s2, s3, s4, s5, s6);
    }

    /**
     * Gets all available table properties
     *
     * @return A List of table properties
     */
    public List<PropertyMetadata<?>> getTableProperties()
    {
        return tableProperties;
    }

    /**
     * Gets the value of the column_mapping property and parses it into a map of Presto column name
     * to a pair of strings, the Accumulo column family and qualifier.
     *
     * @param tableProperties
     *            The map of table properties
     * @return The column mapping, presto name to (accumulo column family, qualifier)
     */
    public static Map<String, Pair<String, String>> getColumnMapping(
            Map<String, Object> tableProperties)
    {
        String strMapping = (String) tableProperties.get(COLUMN_MAPPING);
        if (strMapping == null || strMapping.isEmpty()) {
            throw new InvalidParameterException("Must specify column mapping property");
        }

        // Parse out the column mapping
        // This is a comma-delimited list of "presto column:accumulo fam:accumulo qualifier"
        // triplets
        Map<String, Pair<String, String>> mapping = new HashMap<>();
        for (String m : strMapping.split(",")) {
            String[] tokens = m.split(":");

            // If there are three tokens, parse out the mapping
            // Else throw an exception!
            if (tokens.length == 3) {
                mapping.put(tokens[0], Pair.of(tokens[1], tokens[2]));
            }
            else {
                throw new InvalidParameterException(String
                        .format("Mapping of %s contains %d tokens instead of 3", m, tokens.length));
            }
        }

        return mapping;
    }

    /**
     * Gets the list of all indexed columns set in the table properties
     *
     * @param tableProperties
     *            The map of table properties
     * @return The list of indexed columns, or an empty list if there are none
     */
    public static List<String> getIndexColumns(Map<String, Object> tableProperties)
    {
        return Arrays.asList(StringUtils.split((String) tableProperties.get(INDEX_COLUMNS), ','));
    }

    /**
     * Gets the configured locality groups for the table.
     *
     * @param tableProperties
     *            The map of table properties
     * @return The map of locality groups, or null if not set
     */
    public static Map<String, Set<Text>> getLocalityGroups(Map<String, Object> tableProperties)
    {
        String groupStr = (String) tableProperties.get(LOCALITY_GROUPS);
        if (groupStr == null) {
            return null;
        }

        Map<String, Set<Text>> groups = new HashMap<>();

        // Split all configured locality groups
        for (String group : groupStr.split("\\|")) {
            String[] locGroups = group.split(":");

            if (locGroups.length != 2) {
                throw new PrestoException(StandardErrorCode.USER_ERROR,
                        "Locality groups string is malformed");
            }

            String grpName = locGroups[0];
            String[] fams = locGroups[1].split(",");

            Set<Text> famTexts = new HashSet<>();
            groups.put(grpName, famTexts);

            for (String f : fams) {
                famTexts.add(new Text(f));
            }
        }

        return groups;
    }

    /**
     * Gets the configured row ID for the table
     *
     * @param tableProperties
     *            The map of table properties
     * @return The row ID, or null if none was specifically set (use the first column)
     */
    public static String getRowId(Map<String, Object> tableProperties)
    {
        return (String) tableProperties.get(ROW_ID);
    }

    /**
     * Gets the {@link AccumuloRowSerializer} class name to use for this table
     *
     * @param tableProperties
     *            The map of table properties
     * @return The name of the AccumuloRowSerializer class
     */
    public static String getSerializerClass(Map<String, Object> tableProperties)
    {
        return (String) tableProperties.get(SERIALIZER);
    }

    /**
     * Gets a Boolean value indicating whether or not the given table properties have locality
     * groups set.
     *
     * @param tableProperties
     *            The map of table properties
     * @return True if locality groups is set, false otherwise
     */
    public static boolean hasLocalityGroups(Map<String, Object> tableProperties)
    {
        return tableProperties.containsKey(LOCALITY_GROUPS);
    }

    /**
     * Gets a Boolean value indicating whether or not this table is external and Presto should only
     * manage metadata
     *
     * @param tableProperties
     *            The map of table properties
     * @return True if the table is external, false otherwise
     */
    public static boolean isExternal(Map<String, Object> tableProperties)
    {
        return (Boolean) tableProperties.get(EXTERNAL);
    }
}
