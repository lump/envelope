#!/usr/bin/perl

# $Id: migrate.pl,v 1.24 2010/12/23 19:19:02 troy Exp $
#
# migrate troy's existing live envelope database
# requires a fresh database (bootstrap-mysql.sql)
#
# This is a one-off written in 2010 against a legacy "budgets" database on
# another host.  It is kept because it documents how the old shape maps onto this
# one, not because it is part of the build.
#
# Against the current schema its SQL still stands: every statement below was run
# against a database freshly loaded from bootstrap-mysql.sql and all of them work.
# Two things about that schema are worth knowing here, because this script writes
# columns Hibernate now treats as its own:
#
#   * `stamp` is @Version.  The columns are timestamp(3) (see migrations/001), and
#     the legacy stamps this script carries over are whole seconds, which land as
#     .000 and are perfectly valid version values.  Passing NULL still means "now",
#     as the accounts insert below relies on.
#
#   * accounts is unique on (budget, name) rather than the old (name, type) (see
#     migrations/002), so the `replace into accounts` below can no longer collide
#     with an identically named account in somebody else's budget.  It creates a
#     fresh budget each run, so in practice it never collides at all.
#
# A database loaded from the current bootstrap already has both of those, so a
# migration run needs no migrations applied afterwards.
#
# Connection details come from the environment.  Nothing here is defaulted that
# would let it reach a real host by accident:
#
#   MIGRATE_SRC_HOST  MIGRATE_SRC_PORT  MIGRATE_SRC_DB  MIGRATE_SRC_USER  MIGRATE_SRC_PASSWORD
#   MIGRATE_DST_HOST  MIGRATE_DST_PORT  MIGRATE_DST_DB  MIGRATE_DST_USER  MIGRATE_DST_PASSWORD
#
# Only MIGRATE_SRC_PASSWORD has no default, because it is the only one that
# reaches a machine that is not this one.
#
# e.g.
#   MIGRATE_SRC_HOST=iota.lump MIGRATE_SRC_PASSWORD=... \
#   MIGRATE_DST_PORT=13306 MIGRATE_DST_PASSWORD=tegdub perl sql/migrate.pl

use DBI;

$|=1;

# DBD::mysql is not installed everywhere any more; DBD::MariaDB speaks the same
# DSN and is what this stack has.  Prefer it, fall back to the old driver.
$driver = eval { require DBD::MariaDB; 1 } ? "MariaDB"
        : eval { require DBD::mysql;   1 } ? "mysql"
        : die "neither DBD::MariaDB nor DBD::mysql is installed\n";

sub env {
  my ($name, $default) = @_;
  return $ENV{$name} if defined $ENV{$name} && length $ENV{$name};
  return $default if defined $default;
  die "$name must be set (see the header of this script)\n";
}

$dbs = {
  source => {
    database => env("MIGRATE_SRC_DB", "budgets"),
    port     => env("MIGRATE_SRC_PORT", 3306),
    user     => env("MIGRATE_SRC_USER", "budget"),
    host     => env("MIGRATE_SRC_HOST", "iota.lump"),
    # no default: this one reaches a real host, so it has to be supplied
    password => env("MIGRATE_SRC_PASSWORD"),
  },
  dest => {
    database => env("MIGRATE_DST_DB", "envelope"),
    port     => env("MIGRATE_DST_PORT", 3306),
    user     => env("MIGRATE_DST_USER", "budget"),
    host     => env("MIGRATE_DST_HOST", "localhost"),
    # the local development password, already in bootstrap-mysql.sql and compose.yml
    password => env("MIGRATE_DST_PASSWORD", "tegdub"),
  },
};

for my $db (keys %$dbs) {
    my $this = $dbs->{$db};
    my $dsn = "DBI:$driver:database=" . $this->{database} . ";"
      . "host=" . $this->{host} . ";"
      . "port=" . $this->{port};
    $this->{connection} = DBI->connect($dsn, $this->{user}, $this->{password})
      or die "could not connect to $db ($this->{host}): $DBI::errstr\n";
    ${db} = $this->{connection};
}

# dump the entire source database into hashes
# for my $table (qw(categories settings transactions users)) {
#   $dbs->{source}->{$table} = [];
#   $sth = $dbs->{source}->{connection}->prepare("select * from $table") or die $source->errstr;
#   $sth->execute or die $sth->errstr;
#   while (my $row = $sth->fetchrow_hashref()) {
#     push @{$dbs->{source}->{$table}}, $row;
#   }
#   $sth->finish;
# }


$dbs->{dest}->{connection}->do("replace into budgets values (null, null, 'Bowman')")
  or die $dbs->{source}->{connection}->errstr;
($budget_id) = $dbs->{dest}->{connection}->selectrow_array("select id from budgets where id = last_insert_id()")
  or die $dbs->{source}->{connection}->errstr;
print "Budget id: $budget_id\n";

$accounts = {
    Checking => { id => undef, type => 'Debit', rate => 0.005 },
    Savings => { id => undef, type => 'Debit', rate => 0.0141 },
#    DSavings => { id => undef, type => 'Debit', rate => 0.0475 },
#    Tacoma => { id => undef, type => 'Loan', rate => 0.0 },
};

#for my $account (qw(Checking Savings DSavings Tacoma)) {
for my $account (qw(Checking Savings)) {
  my $entry = $accounts->{$account};
  $dbs->{dest}->{connection}->do("replace into accounts values (null, null, ?, ?, ?, ?, 0)",
                                  undef, $budget_id, $account, $entry->{type}, $entry->{rate});
  ($entry->{id}) = $dbs->{dest}->{connection}->selectrow_array("select id from accounts where id = last_insert_id()")
    or die $dbs->{source}->{connection}->errstr;
  print "$account id: $entry->{id}\n";
}

$dsth = $dbs->{dest}->{connection}->prepare("
insert into users (budget,name,real_name,crypt_password,permissions)
values (?, ?, ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;;

$sth = $dbs->{source}->{connection}->prepare("select *,permissions|0 as int_permissions from users where budgetname = 'bowman'")
  or die $dbs->{source}->{connection}->errstr;;
$sth->execute or die $sth->errstr;
while (my $row = $sth->fetchrow_hashref()) {
  $dsth->execute($budget_id, $row->{username}, $row->{real_name}, $row->{crypt_password}, $row->{int_permissions})
    or die $dsth->errstr;
  print "Inserted user $budget_id, $row->{username}, $row->{real_name}, $row->{crypt_password}, $row->{int_permissions}\n";
}

# add a test user for this budget
$dsth->execute($budget_id, 'bowmantest', 'Bowman Test Account','$1$GOyqcoAk$KTE1zfxeTkoXJTcrFKyFi0',7);
$sth->finish;
$dsth->finish;


#$dsth = $dbs->{dest}->{connection}->prepare(
#  "insert into allocation_settings (budget,name,type,reference_date) values (?, ?, ?, ?)")
#  or die $dbs->{source}->{connection}->errstr;;
#$dsth->execute($budget_id, "SOS", 'Biweekly_Payday', '2006-11-30');
#$dsth->finish;
#print "inserted allocation setting $budget_id, SOS, Biweekly_Payday, 2006-11-30\n";
#($allocation_setting_id) = $dbs->{dest}->{connection}->selectrow_array("select id from allocation_settings where id is null");

$dsth = $dbs->{dest}->{connection}->prepare("insert into categories (account, name) values (?, ?)")
  or die $dbs->{source}->{connection}->errstr;

#$dsth2 = $dbs->{dest}->{connection}->prepare("
#insert into category_allocation_settings
#(allocation_setting, category, allocation, allocation_type, auto_deduct)
#values (?, (select id from categories where id is null), ?, ?, ?)")
#  or die $dbs->{source}->{connection}->errstr;

$dsth2 = $dbs->{dest}->{connection}->prepare("
insert into allocation_presets
(budget, name, category, allocation, allocation_type, auto_deduct)
values (?, 'Pay Day', (select id from categories where id = last_insert_id()), ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;

$sth = $dbs->{source}->{connection}->prepare("select * from categories where budgetname = 'bowman'") or die $dbs->{source}->{connection}->errstr;;
$sth->execute or die $sth->errstr;
while (my $row = $sth->fetchrow_hashref()) {
  next if ($row->{category} eq "All Categories");
  my $allocation_amount = $row->{which} eq "fpp"
    ? $row->{fixed_per_paycheck}
    : $row->{which} eq "ppp"
      ? $row->{percent_per_paycheck}
      : $row->{fixed_per_month};

  my $account_id = $accounts->{Checking}->{id};
  if ($row->{category} eq "Savings") { $account_id = $accounts->{Savings}->{id} }
#  elsif ($row->{category} eq "Tithing") { $account_id = $accounts->{DSavings}->{id}; $row->{deducted} = 0 }
  $dsth->execute($account_id, $row->{category})
    or die $dsth->errstr;
  print "inserted account $account_id, $row->{category}\n";

  $dsth2->execute($budget_id, $allocation_amount, ($row->{which} eq "ppp" ? "percent" : "fixed"), $row->{deducted})
     or die $dsth->errstr;
#  $dsth2->execute($allocation_setting_id, $allocation_amount, $row->{which}, $row->{deducted})
#    or die $dsth->errstr;
  # was $allocation_setting_id, whose assignment is commented out just above, so
  # this line only ever printed an empty field.  The preset goes in against the
  # budget, which is what the insert actually used.
  print "inserted preset $budget_id, $allocation_amount, $row->{which}, $row->{deducted}\n";
}
$sth->finish;
$dsth->finish;

my $categories = {};
$sth = $dbs->{dest}->{connection}->prepare("select * from categories where account = ?") or die $dbs->{source}->{connection}->errstr;;
for my $account (keys %$accounts) {
  $sth->execute($accounts->{$account}->{id}) or die $sth->errstr;
  while (my $row = $sth->fetchrow_hashref()) {
    $categories->{$row->{name}} = $row;
  }
}
$sth->finish;

$dtrans = $dbs->{dest}->{connection}->prepare("
insert into transactions (stamp, date, entity, description, reconciled, transfer)
values (?, ?, ?, ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;;

$dalloc = $dbs->{dest}->{connection}->prepare("
insert into allocations (stamp, category, transaction, amount)
values (?, ?, ?, ?)")
  or die $dbs->{source}->{connection}->errstr;;

my ($transaction_count) = $dbs->{source}->{connection}->selectrow_array("select count(*) from transactions where budgetname = 'bowman'");
$sth = $dbs->{source}->{connection}->prepare("select * from transactions where budgetname = 'bowman' order by date, to_from, description, stamp") or die $dbs->{source}->{connection}->errstr;;
$sth->execute or die $sth->errstr;

my $transaction = undef;
my @allocations = ();

print "Inserting transactions(X) and allocations(a) (skipping beginning balance(-))\n";
my $rownum = 0;
while (my $row = $sth->fetchrow_hashref()) {
  $rownum ++;
  # skip beginning balances because we don't use those anymore
  if (($row->{amount} == 0 and $row->{date} =~ /^2003-01-01$/)
      or ($row->{date} =~ /^20[01][0-9]-01-01$/ and $row->{subcategory} eq "Beginning Balance")) {
    print "-";
    next;
  }

  # sanify nulls in to_from and description
  if ($row->{to_from} eq undef) { $row->{to_from} = "" }
  if ($row->{description} eq undef) { $row->{description} = "" }

  if ($row->{category} =~ /^(Car Payment|Car Maintenance)$/) {
    $row->{description} = $row->{subcategory} . ": " . $row->{description};
  }

  # evaluate if this transaction is the same as the last one.
  my $same = 0;
  if ($row->{date} eq $transaction->{date} and $transaction ne undef) {
    $same = 1 if ($row->{subcategory} =~ /^Payday|Adjustment$/
                  and $transaction->{subcategory} =~ /^Payday|Adjustment$/
                  and ($transaction->{to_from} =~ /^SOS|ArosNet|America First|Original Amount|$/
                  and $row->{to_from} eq $transaction->{to_from}));
    $same = 1 if ($transaction->{to_from} eq $row->{to_from} and $transaction->{description} eq $row->{description});
    $same = 1 if ($transaction->{description} =~ /^Discover\s+.*/i and $row->{description} =~ /^Discover\s+.*/i);
    $same = 1 if ($transaction->{description} =~ /^American Express\s+.*/i and $row->{description} =~ /^American Express\s+.*/i);
    $same = 1 if ($transaction->{description} =~ /^From ATM\s+.*/i and $row->{description} =~ /^From ATM\s+.*/i);
    $same = 1 if ($transaction->{description} =~ /^check\s*#(\d+(?:\.\d+)?).*/i and $row->{description} =~ /^check\s*#$1.*/i);
    $same = 1 if ($transaction->{description} =~ /^part\s+of\s*(\d+(?:\.\d+)?)/i and $row->{description} =~ /^part\s+of\s*$1/i);
  }

  # if this row is considered the same transaction as the last row or the last row, add it to the allocation list
  if ($same) {
    if ($row->{description} =~ /^part\s+of\s*\d+(?:\.\d+)?\s+(?:-\s*)?(.+?)$/i) {
      $transaction->{new_description} .= "; $1"
    }
    if ($row->{description} =~ /^(?:discover(?:\s+Card)?|american express)?\s+(?:-\s*)?(.+?)$/i) {
      $transaction->{new_description} .= "; $1"
    }
    push @allocations, $row;
  }
  # if this row isn't the same, we've got another transaction, process our queue now.
  else {
    if ($transaction ne undef and exists $transaction->{stamp} and length $transaction->{stamp}) {
      insert_transaction($transaction, @allocations);
    }

    $row->{new_description} = $row->{description};
    # nuke part of, as the description is no longer part of anything, it's joined.
    $row->{new_description} =~ s/^(?:part\s+of\s*\d+(?:\.\d+)?|)(?:discover(?:\s+Card)?|american express)?(?:\s*\-\s*)?\s*//i;

    # next transaction to work with is the new one we just got
    $transaction = $row;
    # clear allocations for next transaction
    @allocations = ();
    # push the current row into allocations
    push @allocations, $row;
  }

  # enter final transaction on last row
  if ($rownum == $transaction_count) {
    insert_transaction($transaction, @allocations);
  }
}
print "\n";
$sth->finish;
$dtrans->finish;
$dalloc->finish;

sub insert_transaction {
  my ($transaction, @allocations) = @_;

  print "\ninserting (desc: $transaction->{new_description}) (entity: $transaction->{to_from}) (subcategory: $transaction->{subcategory})\n";
  # add the transaction
  my @params = ($transaction->{stamp},
                $transaction->{date},
                $transaction->{to_from},
                $transaction->{new_description},
                $transaction->{reconciled},
                exists $categories->{$transaction->{to_from}} ? 1 : 0);

  unless ($dtrans->execute(@params)) {
    print "transaction: " . (join ",", @params) . "\n";
    die $dtrans->errstr;
  }

  print "X";
  # the transaction id for the allocations
  my ($last_id) = $dbs->{dest}->{connection}->selectrow_array("select id from transactions where id = last_insert_id()");

  # add allocations to this transaction
  for my $allocation (@allocations) {
  # allocations
    my @params = ($allocation->{stamp}, $categories->{$allocation->{category}}->{id}, $last_id, $allocation->{amount});
    unless ($dalloc->execute(@params)) {
      print "allocation: " . (join ",", @params) . "\n";
      die $dalloc->errstr;
    }
  #my ($allocation_id) = $dbs->{dest}->{connection}->selectrow_array("select id from allocations where id is null");
   print "a";
  }
}
