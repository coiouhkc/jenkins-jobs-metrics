package org.abratuhi.jenkins.service;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MutinyPlayground {
  @Test
  void convertListOfUniToUniOfList() {
    List<Integer> numbers = Uni.combine().all().unis(
       Uni.createFrom().item(1),
       Uni.createFrom().item(2),
       Uni.createFrom().item(3)
    ).combinedWith(objects -> objects.stream().map(o -> (Integer) o).collect(Collectors.toList()))
       .await()
       .indefinitely();

    assertNotNull(numbers);
    assertEquals(3, numbers.size());
  }

  @Test
  void convertStreamOfUniToUniOfList() {
    List<Integer> numbers = Uni.combine().all().unis(
       IntStream.rangeClosed(1, 20)
          .boxed()
          .map(i -> Uni.createFrom().item(i))
          .collect(Collectors.toList())
    ).combinedWith(objects -> objects.stream().map(o -> (Integer) o).collect(Collectors.toList()))
       .await()
       .indefinitely();

    assertNotNull(numbers);
    assertEquals(20, numbers.size());
  }

  @Test
  void convertListOfUniOfListToUniOfList() {
    List<Integer> numbers = Uni.combine().all().unis(
       Uni.createFrom().item(Collections.singletonList(1)),
       Uni.createFrom().item(Collections.singletonList(1)),
       Uni.createFrom().item(Collections.singletonList(1))
    ).combinedWith(objects -> objects.stream().flatMap(l -> ((List<Integer>) l).stream()).collect(Collectors.toList()))
       .await()
       .indefinitely();

    assertNotNull(numbers);
    assertEquals(3, numbers.size());
  }

  @Test
  void smth() {
    List<Uni<Integer>> lUni = IntStream.rangeClosed(1, 20)
       .boxed()
       .map(i -> Uni.createFrom().item(i))
       .collect(Collectors.toList());

    Uni<List<Integer>> numbersUni =
       Uni.createFrom().item(1)
          .flatMap(v ->
             Uni.combine().all().unis(lUni)
                .combinedWith(objects ->
                   objects.stream()
                      .map(o -> (Integer) o)
                      .collect(Collectors.toList()))
          );

    List<Integer> result = Uni.combine().all().unis(
       Uni.createFrom().item(21),
       numbersUni
    ).asTuple()
       .map(tuple -> {
         List<Integer> l = new ArrayList<>();
         l.addAll(tuple.getItem2());
         l.add(tuple.getItem1());
         return l;
       }).await().indefinitely();

    assertNotNull(result);
  }

  @Test
  void smth2() {
    Uni<List<Uni<List<Integer>>>> u1 =
       Uni.createFrom().item(
          IntStream.rangeClosed(0, 9)
             .boxed()
             .map(i -> Uni.createFrom().item(
                IntStream.rangeClosed(i * 10, i * 10 + 10)
                   .boxed()
                   .collect(Collectors.toList())
             ))
             .collect(Collectors.toList())
       );

    Uni<List<Integer>> u2 = u1.flatMap(unis ->
       Uni.combine().all().unis(unis)
          .combinedWith(objects ->
             (List<List<Integer>>) objects)
          .map(lists -> lists.stream()
             .flatMap(Collection::stream)
             .collect(Collectors.toList()))
    );

    List<Integer> result = u2.await().indefinitely();

    assertNotNull(result);
    assertEquals(110, result.size());
  }


  @Test
  void smth3() {
    Uni<List<Uni<List<Integer>>>> u1 =
       Uni.createFrom().item(1)
          .map(integer ->
             IntStream.rangeClosed(0, 9)
                .boxed()
                .map(i -> Uni.createFrom().item(
                   IntStream.rangeClosed(i * 10, i * 10 + 9)
                      .boxed()
                      .collect(Collectors.toList())
                ))
                .collect(Collectors.toList())
          );

    Uni<List<Integer>> u2 = u1.flatMap(unis ->
       Uni.combine().all().unis(unis)
          .combinedWith(objects ->
             ((List<List<Integer>>) objects).stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList()))
    );

    List<Integer> result = u2.await().indefinitely();

    assertNotNull(result);
    assertEquals(100, result.size());
  }

  @Test
  void smth4() {
    Uni<List<Integer>> u1 =
       Uni.createFrom().item(1)
          .map(integer ->
             IntStream.rangeClosed(0, 9)
                .boxed()
                .map(i -> Uni.createFrom().item(
                   IntStream.rangeClosed(i * 10, i * 10 + 9)
                      .boxed()
                      .collect(Collectors.toList())
                ))
                .map(uni -> uni.subscribe().asCompletionStage())
                .map(CompletableFuture::join)
                .flatMap(Collection::stream)
                .collect(Collectors.toList())
          );

    List<Integer> result = u1.await().indefinitely();

    assertNotNull(result);
    assertEquals(100, result.size());
  }
}
