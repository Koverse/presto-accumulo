-- $ID$
-- TPC-H/TPC-R Product Type Profit Measure Query (Q9)
-- Functional Query Definition
-- Approved February 1998

SET SESSION accumulo.optimize_column_filters_enabled = false;
SET SESSION accumulo.optimize_range_predicate_pushdown_enabled = true;
SET SESSION accumulo.optimize_range_splits_enabled = true;
SET SESSION accumulo.secondary_index_enabled = true;

select
	nation,
	o_year,
	sum(amount) as sum_profit
from
	(
		select
			n.name as nation,
			extract(year from o.orderdate) as o_year,
			l.extendedprice * (1 - l.discount) - ps.supplycost * l.quantity as amount
		from
			${SCHEMA}.nation n2,
			${SCHEMA}.region r,
			${SCHEMA}.part p,
			${SCHEMA}.supplier s,
			${SCHEMA}.lineitem l,
			${SCHEMA}.partsupp ps,
			${SCHEMA}.orders o,
			${SCHEMA}.nation n
		where
			s.suppkey = l.suppkey
			and ps.suppkey = l.suppkey
			and ps.partkey = l.partkey
			and p.partkey = l.partkey
			and o.orderkey = l.orderkey
			and s.nationkey = n.nationkey
			and p.name like '%green%'
	) as profit
group by
	nation,
	o_year
order by
	nation,
	o_year desc;